package com.cyberlearnix.admin.controller;

import com.cyberlearnix.shared.repository.CourseRepository;
import com.cyberlearnix.shared.repository.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentFormResponseRepository responseRepository;

    @GetMapping("/users")
    public ResponseEntity<?> getUserStats() {
        long totalUsers = userRepository.count();
        long admins = userRepository.countByRoleIgnoreCase("admin");
        long teachers = userRepository.countByRolesIgnoreCase(java.util.List.of("teacher", "instructor"));
        long students = userRepository.countByRolesIgnoreCase(java.util.List.of("student", "user"));
        long duals = userRepository.countByRoleIgnoreCase("dual");
        long others = totalUsers - (admins + teachers + students + duals);

        return ResponseEntity.ok(Map.of(
                "totalUsers", totalUsers,
                "admins", admins,
                "teachers", teachers,
                "students", students,
                "duals", duals,
                "others", others
        ));
    }

    @GetMapping("/courses")
    public ResponseEntity<?> getCourseStats() {
        long totalCourses = courseRepository.count();
        long approved = courseRepository.countByStatusIgnoreCase("APPROVED");
        long pending = courseRepository.countByStatusIgnoreCase("PENDING");
        long rejected = courseRepository.countByStatusIgnoreCase("REJECTED");

        return ResponseEntity.ok(Map.of(
                "totalCourses", totalCourses,
                "approvedCourses", approved,
                "pendingModeration", pending,
                "rejected", rejected
        ));
    }

    @GetMapping("/revenue")
    public ResponseEntity<?> getRevenueStats() {
        Double totalRevenue = responseRepository.calculateTotalRevenue();
        long totalOrders = responseRepository.count();
        long successfulOrders = responseRepository.countPaidOrders();
 
        return ResponseEntity.ok(Map.of(
                "totalRevenue", totalRevenue != null ? totalRevenue : 0.0,
                "totalOrders", totalOrders,
                "totalSuccessfulOrders", successfulOrders
        ));
    }
}
