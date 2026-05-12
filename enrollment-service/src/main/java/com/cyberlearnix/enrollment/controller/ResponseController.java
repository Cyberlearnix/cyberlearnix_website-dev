package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.enrollment.client.CourseServiceClient;
import com.cyberlearnix.enrollment.service.EnrollmentService;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.entity.enrollment.PaymentTransaction;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentSubmissionRepository;
import com.cyberlearnix.shared.repository.enrollment.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments/responses")
public class ResponseController {

    private static final String KEY_PAYMENT_REQUIRED = "paymentRequired";
    private static final String KEY_MESSAGE = "message";

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseServiceClient courseServiceClient;

    @Autowired
    private EnrollmentSubmissionRepository submissionRepository;

    @Autowired
    private EnrollmentFormResponseRepository responseRepository;

    @Autowired
    private EnrollmentFormConfigRepository configRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getResponses(@RequestParam(required = false) String view) {
        if ("trash".equals(view)) {
            return ResponseEntity.ok(enrollmentService.getTrashedResponses());
        }
        return ResponseEntity.ok(enrollmentService.getAllResponses());
    }

    @GetMapping("/admin-view")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAdminView(
            @RequestParam(required = false) String paymentStatus) {

        ObjectMapper mapper = new ObjectMapper();
        List<EnrollmentFormResponse> responses = responseRepository.findByDeletedAtIsNull();
        List<Map<String, Object>> result = new ArrayList<>();

        for (EnrollmentFormResponse r : responses) {
            if (paymentStatus != null && !paymentStatus.isBlank()
                    && !paymentStatus.equalsIgnoreCase(r.getPaymentStatus())) {
                continue;
            }

            // Resolve student name from studentData JSON, fall back to email
            String studentName = r.getStudentEmail();
            try {
                if (r.getStudentData() != null && !r.getStudentData().isBlank()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> sd = mapper.readValue(r.getStudentData(), Map.class);
                    Object name = sd.getOrDefault("studentName", sd.getOrDefault("name",
                            sd.getOrDefault("fullName", null)));
                    if (name != null && !name.toString().isBlank()) {
                        studentName = name.toString();
                    }
                }
            } catch (Exception ignored) { }

            // Resolve course info from form config, then fetch actual course name
            String courseTitle = null;
            Long courseId = null;
            List<Long> courseIds = null;
            Double coursePrice = null;
            try {
                EnrollmentFormConfig config = configRepository.findById(r.getFormId()).orElse(null);
                if (config != null) {
                    courseId = config.getCourseId();
                    courseIds = config.getEffectiveCourseIds();
                    coursePrice = config.getPaymentAmount();
                    // Fetch real course name — use first effective courseId
                    Long primaryCourseId = (courseIds != null && !courseIds.isEmpty()) ? courseIds.get(0) : courseId;
                    if (primaryCourseId != null) {
                        try {
                            java.util.Map<String, Object> courseInfo = courseServiceClient.getCourseInfo(primaryCourseId);
                    coursePrice = config.getPaymentAmount();
                    // Fetch the real course name from course-service
                    if (courseId != null) {
                        try {
                            java.util.Map<String, Object> courseInfo = courseServiceClient.getCourseInfo(courseId);
                            if (courseInfo != null && courseInfo.get("title") != null) {
                                courseTitle = courseInfo.get("title").toString();
                            }
                        } catch (Exception ignored) { }
                    }
                    // Fall back to form title if course lookup fails
                    if (courseTitle == null) {
                        courseTitle = config.getTitle();
                    }
                }
            } catch (Exception ignored) { }

            // Resolve latest transaction details
            String txnMode = r.getPaymentMode();
            String txnMihpayid = r.getMihpayid();
            String bankRefNum = null;
            String txnStatus = null;
            String initiatedTxnId = null;
            try {
                PaymentTransaction txn = paymentTransactionRepository
                        .findTopByFormResponseIdOrderByInitiatedAtDesc(r.getId()).orElse(null);
                if (txn != null) {
                    if (txnMode == null) txnMode = txn.getMode();
                    if (txnMihpayid == null) txnMihpayid = txn.getMihpayid();
                    bankRefNum = txn.getBankRefNum();
                    txnStatus = txn.getStatus();
                    initiatedTxnId = txn.getTxnid();
                }
            } catch (Exception ignored) { }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.getId());            // alias for frontend compatibility
            item.put("responseId", r.getId());
            item.put("formId", r.getFormId());    // needed for approve/reject actions
            item.put("studentEmail", r.getStudentEmail());
            item.put("studentName", studentName);
            item.put("studentData", r.getStudentData()); // raw JSON for Eye modal
            item.put("courseTitle", courseTitle);
            item.put("courseId", courseId);
            item.put("courseIds", courseIds);      // all linked courses for multi-enrollment
            item.put("coursePrice", coursePrice);  // original price from form config
            item.put("amountPaid", r.getAmountPaid() != null ? r.getAmountPaid() : coursePrice);
            item.put("paymentStatus", r.getPaymentStatus());
            item.put("transactionId", r.getTransactionId());
            item.put("initiatedTxnId", initiatedTxnId); // txnid from payment_transactions
            item.put("paymentMode", txnMode);
            item.put("mihpayid", txnMihpayid);
            item.put("bankRefNum", bankRefNum);
            item.put("txnTableStatus", txnStatus);
            item.put("createdAt", r.getCreatedAt());
            item.put("reviewedAt", r.getReviewedAt());
            item.put("reviewedBy", r.getReviewedBy());
            item.put("createdUserId", r.getCreatedUserId()); // for re-enroll without user lookup
            result.add(item);
        }

        return ResponseEntity.ok(result);
    }

    // ── Public receipt endpoint — student accesses after PayU redirect (no JWT) ──
    @GetMapping("/{id}/receipt")
    public ResponseEntity<Map<String, Object>> getReceipt(@PathVariable Long id) {
        EnrollmentFormResponse r = responseRepository.findById(id).orElse(null);
        if (r == null || r.getDeletedAt() != null) {
            return ResponseEntity.notFound().build();
        }

        ObjectMapper mapper = new ObjectMapper();

        // Parse student form data from stored JSON
        Map<String, Object> studentData = new LinkedHashMap<>();
        try {
            if (r.getStudentData() != null && !r.getStudentData().isBlank()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sd = mapper.readValue(r.getStudentData(), Map.class);
                studentData.putAll(sd);
            }
        } catch (Exception ignored) { }

        // Resolve course / form info
        String courseTitle = null;
        Long courseId = null;
        Double paymentAmount = null;
        String currency = "INR";
        try {
            EnrollmentFormConfig config = configRepository.findById(r.getFormId()).orElse(null);
            if (config != null) {
                courseTitle = config.getTitle();
                courseId = config.getCourseId();
                paymentAmount = config.getPaymentAmount();
                if (config.getPaymentCurrency() != null) currency = config.getPaymentCurrency();
            }
        } catch (Exception ignored) { }

        // Resolve latest transaction details for mode / mihpayid / bankRefNum
        String txnMode = r.getPaymentMode();
        String txnMihpayid = r.getMihpayid();
        String bankRefNum = null;
        String initiatedTxnId = null;
        try {
            PaymentTransaction txn = paymentTransactionRepository
                    .findTopByFormResponseIdOrderByInitiatedAtDesc(r.getId()).orElse(null);
            if (txn != null) {
                if (txnMode == null) txnMode = txn.getMode();
                if (txnMihpayid == null) txnMihpayid = txn.getMihpayid();
                bankRefNum = txn.getBankRefNum();
                initiatedTxnId = txn.getTxnid(); // always available even before PayU callback
            }
        } catch (Exception ignored) { }

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("responseId", r.getId());
        receipt.put("courseTitle", courseTitle);
        receipt.put("courseId", courseId);
        receipt.put("currency", currency);
        receipt.put("coursePrice", paymentAmount);    // original course price from form config
        // amountPaid: use actual paid amount when available, fall back to course price
        receipt.put("amountPaid", r.getAmountPaid() != null ? r.getAmountPaid() : paymentAmount);
        receipt.put("paymentStatus", r.getPaymentStatus());
        receipt.put("transactionId", r.getTransactionId());
        receipt.put("initiatedTxnId", initiatedTxnId); // TXN-... from payment_transactions
        receipt.put("paymentMode", txnMode);
        receipt.put("mihpayid", txnMihpayid);
        receipt.put("bankRefNum", bankRefNum);
        receipt.put("studentEmail", r.getStudentEmail());
        receipt.put("submittedAt", r.getCreatedAt());
        receipt.put("reviewedAt", r.getReviewedAt());
        receipt.put("studentData", studentData);

        return ResponseEntity.ok(receipt);
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkResponse(@RequestParam String formId, @RequestParam String email) {
        boolean alreadyResponded = responseRepository.existsByFormIdAndStudentEmailAndDeletedAtIsNull(formId, email);
        return ResponseEntity.ok(Map.of("alreadyResponded", alreadyResponded));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submitResponse(@RequestBody EnrollmentFormResponse response) {
        EnrollmentFormResponse saved = enrollmentService.submitResponse(response);

        // Tell the frontend whether this form requires payment
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("response", saved);

        configRepository.findById(saved.getFormId()).ifPresent(config -> {
            result.put(KEY_PAYMENT_REQUIRED, config.isPaymentEnabled());
            if (config.isPaymentEnabled()) {
                result.put("paymentAmount", config.getPaymentAmount());
                result.put("paymentCurrency", config.getPaymentCurrency());
                result.put("formResponseId", saved.getId());
                result.put(KEY_MESSAGE,
                        "Form submitted. Please complete payment of "
                                + config.getPaymentCurrency() + " " + config.getPaymentAmount()
                                + " to confirm your enrollment.");
            } else {
                result.put(KEY_MESSAGE, "Form submitted successfully.");
            }
        });

        if (!result.containsKey(KEY_PAYMENT_REQUIRED)) {
            result.put(KEY_PAYMENT_REQUIRED, false);
            result.put(KEY_MESSAGE, "Form submitted successfully.");
        }

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteResponse(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean legacy,
            @RequestParam(defaultValue = "false") boolean permanent) {
        if (permanent) {
            if (legacy) {
                submissionRepository.deleteById(id);
            } else {
                responseRepository.deleteById(id);
            }
        } else {
            enrollmentService.softDeleteResponse(id, legacy);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restoreResponse(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean legacy) {
        if (legacy) {
            submissionRepository.findById(id).ifPresent(s -> {
                s.setDeletedAt(null);
                submissionRepository.save(s);
            });
        } else {
            responseRepository.findById(id).ifPresent(r -> {
                r.setDeletedAt(null);
                responseRepository.save(r);
            });
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/bulk")
    public ResponseEntity<java.util.List<EnrollmentFormResponse>> bulkSubmitResponses(
            @RequestBody java.util.List<java.util.Map<String, Object>> responsesData) {
        java.util.List<EnrollmentFormResponse> responses = new java.util.ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (java.util.Map<String, Object> data : responsesData) {
            EnrollmentFormResponse r = new EnrollmentFormResponse();
            r.setFormId((String) data.get("formId"));
            try {
                r.setStudentData(mapper.writeValueAsString(data.get("studentData")));
            } catch (Exception e) {
                r.setStudentData("{}");
            }
            responses.add(r);
        }
        return ResponseEntity.ok(enrollmentService.bulkSubmit(responses));
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<EnrollmentFormResponse> finalizeResponse(@PathVariable Long id,
            @RequestBody java.util.Map<String, Object> payload) {
        return responseRepository.findById(id).map(r -> {
            r.setPaymentStatus("PAID");
            if (payload.containsKey("transactionId")) {
                r.setTransactionId((String) payload.get("transactionId"));
            }
            if (payload.containsKey("paymentTxnid")) {
                r.setTransactionId((String) payload.get("paymentTxnid"));
            }
            if (payload.containsKey("amount")) {
                r.setAmountPaid(Double.valueOf(payload.get("amount").toString()));
            }
            
            EnrollmentFormResponse saved = responseRepository.save(r);

            // Notify Admin
            try {
                enrollmentService.notifyAdminOfPayment(saved);
            } catch (Exception e) {
                System.err.println("Failed to notify admin: " + e.getMessage());
            }

            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnrollmentFormResponse> updateResponse(@PathVariable Long id,
            @RequestBody java.util.Map<String, Object> payload) {
        return responseRepository.findById(id).map(r -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            try {
                if (payload.containsKey("studentData")) {
                    r.setStudentData(mapper.writeValueAsString(payload.get("studentData")));
                }
                if (payload.containsKey("paymentStatus")) {
                    r.setPaymentStatus((String) payload.get("paymentStatus"));
                }
                if (payload.containsKey("transactionId")) {
                    r.setTransactionId((String) payload.get("transactionId"));
                }
                if (payload.containsKey("paymentTxnid")) {
                    r.setTransactionId((String) payload.get("paymentTxnid"));
                }
            } catch (Exception e) {
                return ResponseEntity.status(500).<EnrollmentFormResponse>build();
            }
            return ResponseEntity.ok(responseRepository.save(r));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/by-txn/{txnid}")
    public ResponseEntity<EnrollmentFormResponse> updateResponseByTxn(@PathVariable String txnid,
            @RequestBody java.util.Map<String, Object> payload) {
        return responseRepository.findByTransactionId(txnid).map(r -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            try {
                if (payload.containsKey("studentData")) {
                    r.setStudentData(mapper.writeValueAsString(payload.get("studentData")));
                }
                if (payload.containsKey("paymentStatus")) {
                    r.setPaymentStatus((String) payload.get("paymentStatus"));
                }
                if (payload.containsKey("transactionId")) {
                    r.setTransactionId((String) payload.get("transactionId"));
                }
            } catch (Exception e) {
                return ResponseEntity.status(500).<EnrollmentFormResponse>build();
            }
            return ResponseEntity.ok(responseRepository.save(r));
        }).orElse(ResponseEntity.notFound().build());
    }
}
