package com.cyberlearnix.enrollment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "course-service", url = "${services.course-service.url:http://localhost:8082}")
public interface CourseServiceClient {

    @GetMapping("/api/courses/teachers/exists")
    boolean teacherExistsForCourse(
            @RequestParam("teacherId") String teacherId,
            @RequestParam("courseId") Long courseId);
}
