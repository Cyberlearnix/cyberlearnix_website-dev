package com.cyberlearnix.course.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "user-service", url = "${services.user-service.url:http://localhost:8081}")
public interface UserServiceClient {

    @GetMapping("/api/users/{userId}/profile")
    Map<String, Object> getUserProfile(@PathVariable("userId") String userId);

    @GetMapping("/api/users/{userId}/teacher-permission")
    Map<String, Object> getTeacherPermission(@PathVariable("userId") String userId);

    @PutMapping("/api/users/{teacherId}/teacher-permission")
    Map<String, Object> updateTeacherPermission(@PathVariable("teacherId") String teacherId,
            @RequestBody Map<String, Object> permissions);
}
