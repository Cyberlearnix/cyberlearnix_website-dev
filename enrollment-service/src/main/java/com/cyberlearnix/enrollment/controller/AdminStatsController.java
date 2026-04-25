package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    @Autowired
    private EnrollmentFormResponseRepository enrollmentFormResponseRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRevenueStats() {
        Double rawRevenue = enrollmentFormResponseRepository.calculateTotalRevenue();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRevenue", rawRevenue != null ? rawRevenue : 0.0);
        stats.put("paidOrders", enrollmentFormResponseRepository.countPaidOrders());
        stats.put("totalEnrollments", enrollmentRepository.count());
        return ResponseEntity.ok(stats);
    }
}
