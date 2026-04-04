package com.cyberlearnix.enrollment.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PaymentController {

    @Value("${payu.merchant.key}")
    private String merchantKey;

    @Value("${payu.merchant.salt}")
    private String merchantSalt;

    @Value("${server.port}")
    private String serverPort;

    @PostMapping("/payu-payment")
    public ResponseEntity<?> initiatePayment(@RequestBody Map<String, Object> payload) {
        try {
            String txnid = "TXN" + System.currentTimeMillis();
            String amount = String.valueOf(payload.get("amount"));
            String productInfo = (String) payload.get("courseName");
            String firstName = (String) payload.get("studentName");
            String email = (String) payload.get("studentEmail");
            String phone = (String) payload.get("studentPhone");

            String formId = (String) payload.get("formId");
            String responseId = String.valueOf(payload.get("enrollmentFormResponseId"));

            // Success and Failure URLs (routed through gateway)
            String surl = "http://localhost:3000/enroll-form.html?status=success&formId=" + formId + "&txnid=" + txnid
                    + "&email=" + email + "&responseId=" + responseId;
            String furl = "http://localhost:3000/enroll-form.html?status=failure&formId=" + formId + "&txnid=" + txnid
                    + "&email=" + email + "&responseId=" + responseId;

            // Generate Hash
            // hash =
            // sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT)
            String hashSequence = String.format("%s|%s|%s|%s|%s|%s|||||||||||%s",
                    merchantKey, txnid, amount, productInfo, firstName, email, merchantSalt);

            String hash = hashCal("SHA-512", hashSequence);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);

            Map<String, String> paymentData = new HashMap<>();
            paymentData.put("key", merchantKey);
            paymentData.put("txnid", txnid);
            paymentData.put("amount", amount);
            paymentData.put("productinfo", productInfo);
            paymentData.put("firstname", firstName);
            paymentData.put("email", email);
            paymentData.put("phone", phone);
            paymentData.put("surl", surl);
            paymentData.put("furl", furl);
            paymentData.put("hash", hash);
            paymentData.put("action", "https://secure.payu.in/_payment");

            response.put("paymentData", paymentData);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Payment initialization failed: " + e.getMessage()));
        }
    }

    private String hashCal(String type, String str) {
        byte[] hashseq = str.getBytes(StandardCharsets.UTF_8);
        StringBuilder hexString = new StringBuilder();
        try {
            MessageDigest algorithm = MessageDigest.getInstance(type);
            algorithm.reset();
            algorithm.update(hashseq);
            byte[] messageDigest = algorithm.digest();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1)
                    hexString.append("0");
                hexString.append(hex);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return hexString.toString();
    }
}
