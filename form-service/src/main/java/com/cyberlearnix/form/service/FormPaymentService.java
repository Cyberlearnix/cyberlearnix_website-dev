package com.cyberlearnix.form.service;

import com.cyberlearnix.shared.entity.form.GeneralForm;
import com.cyberlearnix.shared.entity.form.GeneralFormResponse;
import com.cyberlearnix.shared.entity.enrollment.PaymentTransaction;
import com.cyberlearnix.shared.repository.form.GeneralFormRepository;
import com.cyberlearnix.shared.repository.form.GeneralFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class FormPaymentService {

    @Lazy
    @Autowired
    private FormPaymentService self;

    @Value("${payu.merchant.key}")
    private String merchantKey;

    @Value("${payu.merchant.salt}")
    private String merchantSalt;

    @Value("${payu.base.url:https://secure.payu.in}")
    private String payuBaseUrl;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final PaymentTransactionRepository transactionRepository;

    private final GeneralFormResponseRepository responseRepository;

    private final GeneralFormRepository formRepository;

    public FormPaymentService(PaymentTransactionRepository transactionRepository,
                               GeneralFormResponseRepository responseRepository,
                               GeneralFormRepository formRepository) {
        this.transactionRepository = transactionRepository;
        this.responseRepository = responseRepository;
        this.formRepository = formRepository;
    }

    // ── Initiate Payment ──────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> initiatePayment(Long formResponseId, String studentName,
            String studentEmail, String studentPhone) {

        GeneralFormResponse response = responseRepository.findById(formResponseId)
                .orElseThrow(() -> new RuntimeException("Form response not found: " + formResponseId));
        GeneralForm form = formRepository.findById(response.getFormId())
                .orElseThrow(() -> new RuntimeException("Form not found: " + response.getFormId()));

        if (!form.isPaymentEnabled()) {
            throw new RuntimeException("This form does not require payment.");
        }
        if (form.getTotalAmount() == null || form.getTotalAmount() <= 0) {
            throw new RuntimeException("Payment amount is not configured for this form.");
        }

        String txnid = "FTXN" + System.currentTimeMillis();
        String amount = String.format("%.2f", form.getTotalAmount());
        String productInfo = form.getTitle();

        PaymentTransaction txn = new PaymentTransaction();
        txn.setTxnid(txnid);
        txn.setFormResponseId(formResponseId);
        txn.setFormId(response.getFormId());
        txn.setStudentEmail(studentEmail);
        txn.setStudentName(studentName);
        txn.setStudentPhone(studentPhone);
        txn.setAmount(form.getTotalAmount());
        txn.setCurrency("INR");
        txn.setProductInfo(productInfo);
        txn.setStatus("PENDING");
        txn.setInitiatedAt(LocalDateTime.now());
        transactionRepository.save(txn);

        // Callback URLs point to the public form page with status params
        String token = form.getToken();
        String surl = frontendUrl + "/forms/?id=" + response.getFormId()
                + "&token=" + token + "&payment_status=success&txnid=" + txnid
                + "&responseId=" + formResponseId;
        String furl = frontendUrl + "/forms/?id=" + response.getFormId()
                + "&token=" + token + "&payment_status=failure&txnid=" + txnid
                + "&responseId=" + formResponseId;

        // PayU hash: key|txnid|amount|productinfo|firstname|email|udf1..udf5||||||SALT
        String hashString = merchantKey + "|" + txnid + "|" + amount + "|" + productInfo + "|"
                + studentName + "|" + studentEmail + "|||||||||||" + merchantSalt;
        String hash = sha512(hashString);

        Map<String, String> paymentData = new LinkedHashMap<>();
        paymentData.put("key", merchantKey);
        paymentData.put("txnid", txnid);
        paymentData.put("amount", amount);
        paymentData.put("productinfo", productInfo);
        paymentData.put("firstname", studentName);
        paymentData.put("email", studentEmail);
        paymentData.put("phone", studentPhone != null ? studentPhone : "");
        paymentData.put("surl", surl);
        paymentData.put("furl", furl);
        paymentData.put("hash", hash);
        paymentData.put("action", payuBaseUrl + "/_payment");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("txnid", txnid);
        result.put("paymentData", paymentData);
        return result;
    }

    // ── PayU Callback ─────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> handleCallback(Map<String, String> params) {
        String status = params.getOrDefault("status", "failure").toLowerCase();
        String txnid = params.get("txnid");
        String mihpayid = params.getOrDefault("mihpayid", "");
        String amount = params.getOrDefault("amount", "0");
        String productinfo = params.getOrDefault("productinfo", "");
        String firstname = params.getOrDefault("firstname", "");
        String email = params.getOrDefault("email", "");
        String receivedHash = params.getOrDefault("hash", "");
        String mode = params.getOrDefault("mode", "");
        String bankRefNum = params.getOrDefault("bank_ref_num", "");
        String errorMessage = params.getOrDefault("error_Message", "");

        // Reverse hash verification
        String reverseHashString = merchantSalt + "|" + status + "|||||||||||" + email + "|"
                + firstname + "|" + productinfo + "|" + amount + "|" + txnid + "|" + merchantKey;
        String computedHash = sha512(reverseHashString);
        boolean hashVerified = computedHash.equalsIgnoreCase(receivedHash);

        Optional<PaymentTransaction> txnOpt = transactionRepository.findByTxnid(txnid);
        PaymentTransaction txn = txnOpt.orElseGet(() -> {
            PaymentTransaction t = new PaymentTransaction();
            t.setTxnid(txnid);
            return t;
        });

        txn.setPayuTxnid(txnid);
        txn.setMihpayid(mihpayid);
        txn.setMode(mode);
        txn.setBankRefNum(bankRefNum);
        txn.setErrorMessage(errorMessage);
        txn.setHashVerified(hashVerified);
        txn.setCompletedAt(LocalDateTime.now());

        String normalizedStatus = "success".equals(status) && hashVerified ? "SUCCESS" : "FAILURE";
        txn.setStatus(normalizedStatus);
        transactionRepository.save(txn);

        if (txn.getFormResponseId() != null) {
            responseRepository.findById(txn.getFormResponseId()).ifPresent(r -> {
                r.setPaymentStatus("SUCCESS".equals(normalizedStatus) ? "PAID" : "FAILED");
                r.setTransactionId(txnid);
                if ("SUCCESS".equals(normalizedStatus)) {
                    r.setAmountPaid(Double.parseDouble(amount));
                }
                responseRepository.save(r);
            });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", normalizedStatus);
        result.put("txnid", txnid);
        result.put("mihpayid", mihpayid);
        result.put("hashVerified", hashVerified);
        result.put("message", "SUCCESS".equals(normalizedStatus) ? "Payment successful" : "Payment failed or hash mismatch");
        return result;
    }

    @Transactional
    public void handleWebhook(Map<String, String> params) {
        self.handleCallback(params);
    }

    public Map<String, Object> getPaymentStatus(String txnid) {
        PaymentTransaction txn = transactionRepository.findByTxnid(txnid)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + txnid));
        Map<String, Object> result = new LinkedHashMap<>();
        // Transaction details
        result.put("txnid", txn.getTxnid());
        result.put("status", txn.getStatus());
        result.put("amount", txn.getAmount());
        result.put("currency", txn.getCurrency());
        result.put("productInfo", txn.getProductInfo());
        result.put("mihpayid", txn.getMihpayid());
        result.put("mode", txn.getMode());
        result.put("bankRefNum", txn.getBankRefNum());
        result.put("errorMessage", txn.getErrorMessage());
        result.put("hashVerified", txn.isHashVerified());
        result.put("initiatedAt", txn.getInitiatedAt());
        result.put("completedAt", txn.getCompletedAt());
        result.put("formId", txn.getFormId());
        result.put("formResponseId", txn.getFormResponseId());
        // Student details (snapshot captured at payment initiation)
        result.put("studentName", txn.getStudentName());
        result.put("studentEmail", txn.getStudentEmail());
        result.put("studentPhone", txn.getStudentPhone());
        // Submission data from the linked form response
        if (txn.getFormResponseId() != null) {
            responseRepository.findById(txn.getFormResponseId()).ifPresent(r -> {
                result.put("submissionData", r.getSubmissionData());
                result.put("paymentStatus", r.getPaymentStatus());
                result.put("amountPaid", r.getAmountPaid());
                result.put("submittedAt", r.getCreatedAt());
            });
        }
        return result;
    }

    public Map<String, Object> getFormPaymentInfo(String formId) {
        GeneralForm form = formRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Form not found: " + formId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paymentEnabled", form.isPaymentEnabled());
        result.put("courseId", form.getCourseId());
        result.put("paymentAmount", form.getPaymentAmount());
        result.put("gstPercent", form.getGstPercent());
        result.put("gstAmount", form.getGstAmount());
        result.put("totalAmount", form.getTotalAmount());
        return result;
    }

    private String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.reset();
            md.update(input.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Hash computation failed", e);
        }
    }
}
