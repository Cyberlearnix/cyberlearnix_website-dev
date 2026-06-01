package com.cyberlearnix.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "enrollment-service-admin", url = "${services.enrollment-service.url:https://cyberlearnix.com}")
public interface EnrollmentServiceClient {

    @GetMapping("/api/enrollments/orders")
    List<Map<String, Object>> getAllOrders(@RequestParam(required = false) String status,
            @RequestHeader("Authorization") String auth);

    @GetMapping("/api/enrollments/orders/{id}")
    Map<String, Object> getOrderById(@PathVariable("id") Long id,
            @RequestHeader("Authorization") String auth);

    @PutMapping("/api/enrollments/orders/{id}/status")
    Map<String, Object> updateOrderStatus(@PathVariable("id") Long id,
            @RequestBody Map<String, String> statusRequest,
            @RequestHeader("Authorization") String auth);

    @PostMapping("/api/enrollments/orders/{id}/refund")
    Map<String, Object> processRefund(@PathVariable("id") Long id,
            @RequestHeader("Authorization") String auth);

    @GetMapping("/api/admin/stats/revenue")
    Map<String, Object> getRevenueStats(@RequestHeader("Authorization") String auth);

    @GetMapping("/api/admin/reports/courses")
    List<Map<String, Object>> getCourseReport(@RequestHeader("Authorization") String auth);
}
