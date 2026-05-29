package com.cyberlearnix.instructor.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "course-service-instructor", url = "${services.course-service.url:http://localhost:8082}")
public interface CourseServiceClient {

    @GetMapping("/api/courses")
    Map<String, Object> getAllCourses(
            @RequestParam(required = false) String status,
            @RequestHeader("Authorization") String auth);

    @GetMapping("/api/courses/{courseId}")
    Map<String, Object> getCourseById(
            @PathVariable("courseId") Long courseId,
            @RequestHeader("Authorization") String auth);

    @GetMapping("/api/courses/teachers")
    List<Map<String, Object>> getCourseTeachers(
            @RequestParam(required = false) Long teacherId,
            @RequestHeader("Authorization") String auth);

    @GetMapping("/api/courses/{courseId}/content")
    List<Map<String, Object>> getCourseContent(
            @PathVariable("courseId") Long courseId,
            @RequestHeader("Authorization") String auth);

    @PostMapping("/api/course-management/content")
    Map<String, Object> addCourseContent(
            @RequestBody Map<String, Object> contentRequest,
            @RequestHeader("Authorization") String auth);

    @GetMapping("/api/admin/stats/courses")
    Map<String, Object> getCourseStats(@RequestHeader("Authorization") String auth);
}
