package com.cyberlearnix.instructor.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "user-service-instructor", url = "${services.user-service.url:http://localhost:8081}")
public interface UserServiceClient {

    @GetMapping("/api/users/{id}")
    Map<String, Object> getUserById(
            @PathVariable("id") String id,
            @RequestHeader("Authorization") String auth);

    @PutMapping("/api/users/{id}")
    Map<String, Object> updateUser(
            @PathVariable("id") String id,
            @RequestBody Map<String, String> profileRequest,
            @RequestHeader("Authorization") String auth);
}
