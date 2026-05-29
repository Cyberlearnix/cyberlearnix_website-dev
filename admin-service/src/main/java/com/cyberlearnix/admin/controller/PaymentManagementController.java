package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.client.EnrollmentServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class PaymentManagementController {

    private final EnrollmentServiceClient enrollmentServiceClient;

    @GetMapping
    public List<Map<String, Object>> getAllOrders(@RequestParam(required = false) String status,
                                                   @RequestHeader("Authorization") String auth) {
        return enrollmentServiceClient.getAllOrders(status, auth);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOrderDetails(@PathVariable Long id,
                                                                @RequestHeader("Authorization") String auth) {
        try {
            return ResponseEntity.ok(enrollmentServiceClient.getOrderById(id, auth));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id,
                                                @RequestBody Map<String, String> statusRequest,
                                                @RequestHeader("Authorization") String auth) {
        if (statusRequest.get("status") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "status field is required"));
        }
        return ResponseEntity.ok(enrollmentServiceClient.updateOrderStatus(id, statusRequest, auth));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> processRefund(@PathVariable Long id,
                                            @RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(enrollmentServiceClient.processRefund(id, auth));
    }
}
