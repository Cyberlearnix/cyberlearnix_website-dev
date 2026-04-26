package com.cyberlearnix.enrollment.service;

import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.entity.enrollment.PaymentTransaction;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    @Value("${payu.merchant.key}")
    private String merchantKey;

    @Value("${payu.merchant.salt}")
    private String merchantSalt;

    @Value("${payu.base.url:https://secure.payu.in}")
    private String payuBaseUrl;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @Autowired
    private EnrollmentFormResponseRepository responseRepository;

    @Autowired
    private EnrollmentFormConfigRepository configRepository;

    // ── Initiate Payment ──────────────────────────────────────────────────────

    /**
     * Creates a PayU payment initiation payload for a given form response.
     * Saves a PENDING PaymentTransaction record so we can correlate the callback.
     */
    @Transactional
    public Map<String, Object> initiatePayment(Long formResponseId, String studentName,
            String studentEmail, String studentPhone) {

        // 1. Fetch response & form config
        EnrollmentFormResponse response = responseRepository.findById(formResponseId)
                .orElseThrow(() -> new RuntimeException("Form response not found: " + formResponseId));
        EnrollmentFormConfig config = configRepository.findById(response.getFormId())
                .orElseThrow(() -> new RuntimeException("Form not found: " + response.getFormId()));

        if (!config.isPaymentEnabled()) {
            throw new RuntimeException("This form does not require payment.");
        }
        if (config.getPaymentAmount() == null || config.getPaymentAmount() <= 0) {
            throw new RuntimeException("Payment amount is not configured for this form.");
        }

        // 2. Build transaction record
        String txnid = "TXN" + System.currentTimeMillis();
        String amount = String.format("%.2f", config.getPaymentAmount());
        String currency = config.getPaymentCurrency() != null ? config.getPaymentCurrency() : "INR";
        String productInfo = config.getTitle();

        PaymentTransaction txn = new PaymentTransaction();
        txn.setTxnid(txnid);
        txn.setFormResponseId(formResponseId);
        txn.setFormId(response.getFormId());
        txn.setStudentEmail(studentEmail);
        txn.setStudentName(studentName);
        txn.setStudentPhone(studentPhone);
        txn.setAmount(config.getPaymentAmount());
        txn.setCurrency(currency);
        txn.setProductInfo(productInfo);
        txn.setStatus("PENDING");
        txn.setInitiatedAt(LocalDateTime.now());
        transactionRepository.save(txn);

        // 3. Build callback URLs
        String surl = frontendUrl + "/enroll-form.html?status=success&formId=" + response.getFormId()
                + "&txnid=" + txnid + "&email=" + studentEmail + "&responseId=" + formResponseId;
        String furl = frontendUrl + "/enroll-form.html?status=failure&formId=" + response.getFormId()
                + "&txnid=" + txnid + "&email=" + studentEmail + "&responseId=" + formResponseId;

        // 4. Generate PayU hash
        // Format: key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT
        String hashString = merchantKey + "|" + txnid + "|" + amount + "|" + productInfo + "|"
                + studentName + "|" + studentEmail + "|||||||||||" + merchantSalt;
        String hash = sha512(hashString);

        // 5. Build payment data map
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

    // ── PayU Callback (browser redirect) ─────────────────────────────────────

    /**
     * Handles the success/failure redirect from PayU.
     * Verifies the reverse hash and updates transaction + form response status.
     */
    @Transactional
    public Map<String, Object> handleCallback(Map<String, String> params) {
        String status = params.getOrDefault("status", "failure").toLowerCase();
        String txnid = params.get("txnid");
        String payuTxnid = params.get("txnid");
        String mihpayid = params.getOrDefault("mihpayid", "");
        String amount = params.getOrDefault("amount", "0");
        String productinfo = params.getOrDefault("productinfo", "");
        String firstname = params.getOrDefault("firstname", "");
        String email = params.getOrDefault("email", "");
        String receivedHash = params.getOrDefault("hash", "");
        String mode = params.getOrDefault("mode", "");
        String bankRefNum = params.getOrDefault("bank_ref_num", "");
        String errorMessage = params.getOrDefault("error_Message", "");

        // 1. Verify reverse hash
        // Reverse hash: sha512(SALT|status||||||udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
        String reverseHashString = merchantSalt + "|" + status + "|||||||||||" + email + "|"
                + firstname + "|" + productinfo + "|" + amount + "|" + txnid + "|" + merchantKey;
        String computedHash = sha512(reverseHashString);
        boolean hashVerified = computedHash.equalsIgnoreCase(receivedHash);

        // 2. Load and update transaction
        Optional<PaymentTransaction> txnOpt = transactionRepository.findByTxnid(txnid);
        PaymentTransaction txn;
        if (txnOpt.isPresent()) {
            txn = txnOpt.get();
        } else {
            // Fallback: create record if webhook arrived before callback
            txn = new PaymentTransaction();
            txn.setTxnid(txnid);
        }

        txn.setPayuTxnid(payuTxnid);
        txn.setMihpayid(mihpayid);
        txn.setMode(mode);
        txn.setBankRefNum(bankRefNum);
        txn.setErrorMessage(errorMessage);
        txn.setHashVerified(hashVerified);
        txn.setCompletedAt(LocalDateTime.now());

        String normalizedStatus = "success".equals(status) && hashVerified ? "SUCCESS" : "FAILURE";
        txn.setStatus(normalizedStatus);
        transactionRepository.save(txn);

        // 3. Update form response payment status
        if (txn.getFormResponseId() != null) {
            responseRepository.findById(txn.getFormResponseId()).ifPresent(r -> {
                r.setPaymentStatus("SUCCESS".equals(normalizedStatus) ? "PAID" : "FAILED");
                r.setTransactionId(txnid);
                r.setAmountPaid("SUCCESS".equals(normalizedStatus)
                        ? Double.parseDouble(amount) : r.getAmountPaid());
                responseRepository.save(r);
            });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", normalizedStatus);
        result.put("txnid", txnid);
        result.put("mihpayid", mihpayid);
        result.put("hashVerified", hashVerified);
        result.put("message", "SUCCESS".equals(normalizedStatus)
                ? "Payment successful" : "Payment failed or hash mismatch");
        return result;
    }

    // ── PayU Webhook (server-to-server) ──────────────────────────────────────

    /**
     * Handles PayU's server-to-server webhook (same logic as callback but
     * called directly by PayU without browser involvement).
     */
    @Transactional
    public void handleWebhook(Map<String, String> params) {
        // Delegate to same callback handler — PayU sends same fields
        handleCallback(params);
    }

    // ── Status check ─────────────────────────────────────────────────────────

    public Map<String, Object> getPaymentStatus(String txnid) {
        PaymentTransaction txn = transactionRepository.findByTxnid(txnid)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + txnid));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("txnid", txn.getTxnid());
        result.put("status", txn.getStatus());
        result.put("amount", txn.getAmount());
        result.put("currency", txn.getCurrency());
        result.put("mihpayid", txn.getMihpayid());
        result.put("mode", txn.getMode());
        result.put("hashVerified", txn.isHashVerified());
        result.put("initiatedAt", txn.getInitiatedAt());
        result.put("completedAt", txn.getCompletedAt());
        result.put("formResponseId", txn.getFormResponseId());
        return result;
    }

    public Map<String, Object> getStatusByResponseId(Long formResponseId) {
        Optional<PaymentTransaction> txn =
                transactionRepository.findTopByFormResponseIdOrderByInitiatedAtDesc(formResponseId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (txn.isPresent()) {
            result.put("found", true);
            result.put("txnid", txn.get().getTxnid());
            result.put("status", txn.get().getStatus());
            result.put("amount", txn.get().getAmount());
            result.put("mihpayid", txn.get().getMihpayid());
            result.put("hashVerified", txn.get().isHashVerified());
        } else {
            result.put("found", false);
            result.put("status", "NOT_INITIATED");
        }
        return result;
    }

    // ── Util ──────────────────────────────────────────────────────────────────

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
            throw new RuntimeException("Hash computation failed", e);
        }
    }
}
