package com.cyberlearnix.admin.controller;

import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.repository.EnrollmentFormResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class PaymentManagementController {

    private final EnrollmentFormResponseRepository responseRepository;

    @GetMapping
    public List<EnrollmentFormResponse> getAllOrders(@RequestParam(required = false) String status) {
        if (status != null) {
            return responseRepository.findAll().stream()
                    .filter(r -> r.getPaymentStatus().equalsIgnoreCase(status))
                    .toList();
        }
        return responseRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnrollmentFormResponse> getOrderDetails(@PathVariable Long id) {
        return responseRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, String> statusRequest) {
        String status = statusRequest.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "status field is required"));
        }

        return responseRepository.findById(id)
                .map(order -> {
                    order.setPaymentStatus(status);
                    if ("SUCCESS".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status)) {
                        order.setReviewedAt(LocalDateTime.now());
                        // In a real app, this might trigger the actual enrollment creation
                    }
                    responseRepository.save(order);
                    return ResponseEntity.ok(Map.of("message", "Order status updated successfully", "status", status));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> processRefund(@PathVariable Long id) {
        return responseRepository.findById(id)
                .map(order -> {
                    order.setPaymentStatus("REFUNDED");
                    order.setUpdatedAt(LocalDateTime.now());
                    responseRepository.save(order);
                    return ResponseEntity.ok(Map.of("message", "Order marked as REFUNDED", "status", "REFUNDED"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
