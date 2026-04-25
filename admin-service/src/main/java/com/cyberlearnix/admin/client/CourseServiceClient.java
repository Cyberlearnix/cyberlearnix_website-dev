package com.cyberlearnix.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "course-service-admin", url = "${services.course-service.url:http://localhost:8082}")
public interface CourseServiceClient {

    @GetMapping("/api/courses")
    List<Map<String, Object>> getAllCourses(@RequestParam(required = false) String status,
                                             @RequestHeader("Authorization") String auth);

    @PutMapping("/api/courses/{id}/status")
    Map<String, Object> updateCourseStatus(@PathVariable("id") Long id,
                                            @RequestBody Map<String, String> statusRequest,
                                            @RequestHeader("Authorization") String auth);

    @GetMapping("/api/courses/{courseId}/content")
    List<Map<String, Object>> getCourseContent(@PathVariable("courseId") Long courseId,
                                                @RequestHeader("Authorization") String auth);

    @PutMapping("/api/courses/content/{id}/status")
    Map<String, Object> updateContentStatus(@PathVariable("id") Long id,
                                             @RequestBody Map<String, String> statusRequest,
                                             @RequestHeader("Authorization") String auth);

    @GetMapping("/api/admin/stats/courses")
    Map<String, Object> getCourseStats(@RequestHeader("Authorization") String auth);
}
