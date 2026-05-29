package com.cyberlearnix.instructor.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "enrollment-service-instructor", url = "${services.enrollment-service.url:https://cyberlearnix.com}")
public interface EnrollmentServiceClient {

        @GetMapping("/api/enrollments")
        Map<String, Object> getEnrollments(
                        @RequestParam(required = false) String studentId,
                        @RequestParam(required = false) Long courseId,
                        @RequestHeader("Authorization") String auth);

        @GetMapping("/api/enrollments/course/{courseId}/students")
        List<Map<String, Object>> getStudentsByCourse(
                        @PathVariable("courseId") Long courseId,
                        @RequestHeader("Authorization") String auth);

        @GetMapping("/api/enrollments/student/{studentId}/progress")
        Map<String, Object> getStudentProgress(
                        @PathVariable("studentId") String studentId,
                        @RequestParam(required = false) Long courseId,
                        @RequestHeader("Authorization") String auth);

        @GetMapping("/api/enrollments/stats")
        Map<String, Object> getEnrollmentStats(@RequestHeader("Authorization") String auth);
}
