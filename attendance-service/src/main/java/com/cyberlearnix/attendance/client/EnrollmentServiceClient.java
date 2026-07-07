package com.cyberlearnix.attendance.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "enrollment-service", url = "${services.enrollment-service.url:http://localhost:8083}")
public interface EnrollmentServiceClient {

    @GetMapping("/api/enrollments/check")
    Boolean checkEnrollment(@RequestParam("studentId") String studentId, @RequestParam("courseId") Long courseId);
}
