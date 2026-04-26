package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.enrollment.service.EnrollmentService;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentSubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments/responses")
public class ResponseController {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private EnrollmentSubmissionRepository submissionRepository;

    @Autowired
    private EnrollmentFormResponseRepository responseRepository;

    @Autowired
    private EnrollmentFormConfigRepository configRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getResponses(@RequestParam(required = false) String view) {
        if ("trash".equals(view)) {
            return ResponseEntity.ok(enrollmentService.getTrashedResponses());
        }
        return ResponseEntity.ok(enrollmentService.getAllResponses());
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
            result.put("paymentRequired", config.isPaymentEnabled());
            if (config.isPaymentEnabled()) {
                result.put("paymentAmount", config.getPaymentAmount());
                result.put("paymentCurrency", config.getPaymentCurrency());
                result.put("formResponseId", saved.getId());
                result.put("message",
                        "Form submitted. Please complete payment of "
                                + config.getPaymentCurrency() + " " + config.getPaymentAmount()
                                + " to confirm your enrollment.");
            } else {
                result.put("message", "Form submitted successfully.");
            }
        });

        if (!result.containsKey("paymentRequired")) {
            result.put("paymentRequired", false);
            result.put("message", "Form submitted successfully.");
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
