package com.cyberlearnix.course.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "enrollment-service", url = "${services.enrollment-service.url:http://localhost:8083}")
public interface EnrollmentServiceClient {

    @GetMapping("/api/enrollments")
    List<Map<String, Object>> getEnrollments(
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) Long courseId);

    @GetMapping("/api/enrollments/check")
    Boolean isEnrolled(
            @RequestParam String studentId,
            @RequestParam Long courseId);

    @PatchMapping("/api/enrollments/progress")
    void updateProgress(@RequestBody Map<String, Object> progressUpdate);
}
