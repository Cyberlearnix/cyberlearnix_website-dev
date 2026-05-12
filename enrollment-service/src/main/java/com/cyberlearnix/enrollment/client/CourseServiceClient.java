package com.cyberlearnix.enrollment.client;

import com.cyberlearnix.shared.entity.course.Certificate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "course-service", url = "${services.course-service.url:http://localhost:8082}")
public interface CourseServiceClient {

    @GetMapping("/api/courses/teachers/exists")
    boolean teacherExistsForCourse(
            @RequestParam("teacherId") String teacherId,
            @RequestParam("courseId") Long courseId);

    @GetMapping("/api/courses/{id}/price")
    java.util.Map<String, Object> getCoursePrice(@PathVariable("id") Long id);

    @GetMapping("/api/courses/{id}")
    java.util.Map<String, Object> getCourseInfo(@PathVariable("id") Long id);

    @PostMapping("/api/certificates")
    Certificate issueCertificate(@RequestBody Certificate certificate);
}
