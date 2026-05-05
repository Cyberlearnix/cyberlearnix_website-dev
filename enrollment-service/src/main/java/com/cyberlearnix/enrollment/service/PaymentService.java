package com.cyberlearnix.enrollment.service;

import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.entity.enrollment.PaymentTransaction;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.enrollment.client.CourseServiceClient;
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
public class PaymentService {

    @Value("${payu.merchant.key}")
    private String merchantKey;

    @Value("${payu.merchant.salt}")
    private String merchantSalt;

    @Value("${payu.base.url:https://secure.payu.in}")
    private String payuBaseUrl;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private final PaymentTransactionRepository transactionRepository;

    private final EnrollmentFormResponseRepository responseRepository;

    private final EnrollmentFormConfigRepository configRepository;

    private final CourseServiceClient courseServiceClient;

    private final CouponService couponService;

    @Lazy
    @Autowired
    private PaymentService self;

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILURE = "FAILURE";
    private static final String KEY_HASH_VERIFIED = "hashVerified";
    private static final String KEY_TXNID = "txnid";

    public PaymentService(PaymentTransactionRepository transactionRepository,
                          EnrollmentFormResponseRepository responseRepository,
                          EnrollmentFormConfigRepository configRepository,
                          CourseServiceClient courseServiceClient,
                          CouponService couponService) {
        this.transactionRepository = transactionRepository;
        this.responseRepository = responseRepository;
        this.configRepository = configRepository;
        this.courseServiceClient = courseServiceClient;
        this.couponService = couponService;
    }

    // ── Initiate Payment ──────────────────────────────────────────────────────

    /**
     * Creates a PayU payment initiation payload for a given form response.
     * Saves a PENDING PaymentTransaction record so we can correlate the callback.
     */
    @Transactional
    public Map<String, Object> initiatePayment(Long formResponseId, String studentName,
            String studentEmail, String studentPhone, String couponCode) {

        // 1. Fetch response & form config
        EnrollmentFormResponse response = responseRepository.findById(formResponseId)
                .orElseThrow(() -> new RuntimeException("Form response not found: " + formResponseId));
        EnrollmentFormConfig config = configRepository.findById(response.getFormId())
                .orElseThrow(() -> new RuntimeException("Form not found: " + response.getFormId()));

        if (!config.isPaymentEnabled()) {
            throw new RuntimeException("This form does not require payment.");
        }

        // Resolve payment amount: use stored amount, or fall back to course's finalPrice
        Double resolvedAmount = config.getPaymentAmount();
        if ((resolvedAmount == null || resolvedAmount <= 0) && config.getCourseId() != null) {
            try {
                Map<String, Object> coursePrice = courseServiceClient.getCoursePrice(config.getCourseId());
                Object fp = coursePrice.get("finalPrice");
                if (fp != null) {
                    resolvedAmount = ((Number) fp).doubleValue();
                }
            } catch (Exception e) {
                // Feign call failed — cannot determine amount
            }
        }
        if (resolvedAmount == null || resolvedAmount <= 0) {
            throw new RuntimeException("Payment amount is not configured for this form. Please ask the admin to set the course price.");
        }

        // Apply coupon discount if a user-supplied code was provided
        double discountAmount = 0.0;
        String appliedCoupon = null;
        if (couponCode != null && !couponCode.isBlank()) {
            discountAmount = couponService.applyAndConsume(couponCode, resolvedAmount);
            resolvedAmount = Math.max(1.0, resolvedAmount - discountAmount);
            appliedCoupon = couponCode.trim().toUpperCase();
        } else if (config.isDiscountEnabled() && config.getDiscountValue() != null && config.getDiscountValue() > 0) {
            // Form-level discount: auto-apply from form config when admin has enabled it
            double formDiscount;
            if ("PERCENTAGE".equalsIgnoreCase(config.getDiscountType())) {
                formDiscount = resolvedAmount * config.getDiscountValue() / 100.0;
            } else {
                // FLAT discount
                formDiscount = Math.min(config.getDiscountValue(), resolvedAmount - 1);
            }
            formDiscount = Math.max(0, formDiscount);
            discountAmount = parseDouble2dp(formDiscount);
            resolvedAmount = Math.max(1.0, resolvedAmount - discountAmount);
            appliedCoupon = config.getDiscountCouponCode() != null
                    ? config.getDiscountCouponCode() : "FORM-DISCOUNT";
        }

        // 2. Build transaction record
        // BUG-002: Use UUID-based txnid to prevent timestamp collisions under concurrent load
        String txnid = "TXN-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String amount = String.format("%.2f", resolvedAmount);
        String currency = config.getPaymentCurrency() != null ? config.getPaymentCurrency() : "INR";

        // productInfo must not contain pipe '|' — it's used verbatim in the hash
        String productInfo = sanitizeForHash(config.getTitle() != null ? config.getTitle() : "Enrollment");

        // PayU requires a 10-digit phone; use fallback if blank/invalid
        String phone = (studentPhone != null && studentPhone.replaceAll("\\D", "").length() >= 10)
                ? studentPhone.replaceAll("\\D", "").substring(0, 10)
                : "0000000000";

        // firstname must not contain pipe characters
        String firstname = sanitizeForHash(studentName != null ? studentName : "Student");
        String email = studentEmail != null ? studentEmail.trim() : "";

        PaymentTransaction txn = new PaymentTransaction();
        txn.setTxnid(txnid);
        txn.setFormResponseId(formResponseId);
        txn.setFormId(response.getFormId());
        txn.setStudentEmail(email);
        txn.setStudentName(firstname);
        txn.setStudentPhone(phone);
        txn.setAmount(resolvedAmount);
        txn.setCurrency(currency);
        txn.setProductInfo(productInfo);
        txn.setStatus("PENDING");
        txn.setInitiatedAt(LocalDateTime.now());
        if (appliedCoupon != null) {
            txn.setCouponCode(appliedCoupon);
            txn.setDiscountAmount(discountAmount);
        }
        transactionRepository.save(txn);

        // 3. Build callback URLs (browser redirects — must be reachable by the student's browser)
        String encodedEmail = java.net.URLEncoder.encode(email, StandardCharsets.UTF_8);
        String surl = frontendUrl + "/enroll-form.html?status=success&formId=" + response.getFormId()
                + "&txnid=" + txnid + "&email=" + encodedEmail + "&responseId=" + formResponseId;
        String furl = frontendUrl + "/enroll-form.html?status=failure&formId=" + response.getFormId()
                + "&txnid=" + txnid + "&email=" + encodedEmail + "&responseId=" + formResponseId;

        // 4. Generate PayU hash
        // Format: key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT
        // After udf5 there are 6 additional empty fields before SALT (11 pipes total after email)
        String hashString = merchantKey + "|" + txnid + "|" + amount + "|" + productInfo + "|"
                + firstname + "|" + email + "|||||||||||" + merchantSalt;
        String hash = sha512(hashString);

        // 5. Build payment data map
        Map<String, String> paymentData = new LinkedHashMap<>();
        paymentData.put("key", merchantKey);
        paymentData.put(KEY_TXNID, txnid);
        paymentData.put("amount", amount);
        paymentData.put("productinfo", productInfo);
        paymentData.put("firstname", firstname);
        paymentData.put("email", email);
        paymentData.put("phone", phone);
        paymentData.put("surl", surl);
        paymentData.put("furl", furl);
        paymentData.put("hash", hash);
        paymentData.put("action", payuBaseUrl + "/_payment");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put(KEY_TXNID, txnid);
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
        String txnid = params.get(KEY_TXNID);
        String payuTxnid = params.get(KEY_TXNID);
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

        String normalizedStatus = "success".equals(status) && hashVerified ? STATUS_SUCCESS : STATUS_FAILURE;
        txn.setStatus(normalizedStatus);
        transactionRepository.save(txn);

        // 3. Update form response payment status
        if (txn.getFormResponseId() != null) {
            responseRepository.findById(txn.getFormResponseId()).ifPresent(r -> {
                r.setPaymentStatus(STATUS_SUCCESS.equals(normalizedStatus) ? "PAID" : "FAILED");
                r.setTransactionId(txnid);
                if ("SUCCESS".equals(normalizedStatus)) {
                    r.setAmountPaid(Double.parseDouble(amount));
                }
                responseRepository.save(r);
            });
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", normalizedStatus);
        result.put(KEY_TXNID, txnid);
        result.put("mihpayid", mihpayid);
        result.put(KEY_HASH_VERIFIED, hashVerified);
        result.put("message", STATUS_SUCCESS.equals(normalizedStatus)
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
        self.handleCallback(params);
    }

    // ── Status check ─────────────────────────────────────────────────────────

    public Map<String, Object> getPaymentStatus(String txnid) {
        PaymentTransaction txn = transactionRepository.findByTxnid(txnid)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + txnid));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_TXNID, txn.getTxnid());
        result.put("status", txn.getStatus());
        result.put("amount", txn.getAmount());
        result.put("currency", txn.getCurrency());
        result.put("mihpayid", txn.getMihpayid());
        result.put("mode", txn.getMode());
        result.put(KEY_HASH_VERIFIED, txn.isHashVerified());
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
            result.put(KEY_TXNID, txn.get().getTxnid());
            result.put("status", txn.get().getStatus());
            result.put("amount", txn.get().getAmount());
            result.put("mihpayid", txn.get().getMihpayid());
            result.put(KEY_HASH_VERIFIED, txn.get().isHashVerified());
        } else {
            result.put("found", false);
            result.put("status", "NOT_INITIATED");
        }
        return result;
    }

    // ── Util ──────────────────────────────────────────────────────────────────

    /** Round a double to 2 decimal places. */
    private double parseDouble2dp(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Remove pipe '|' characters from a string field used in PayU hash computation.
     * A pipe in productinfo/firstname would corrupt the hash string.
     */
    private String sanitizeForHash(String value) {
        if (value == null) return "";
        return value.replace("|", " ").trim();
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
