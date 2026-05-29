package com.cyberlearnix.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "user-service", url = "${services.user-service.url:http://localhost:8081}")
public interface AuthServiceClient {

    @PostMapping("/api/auth/login")
    Map<String, Object> login(@RequestBody Map<String, String> loginRequest);

    @PostMapping("/api/auth/logout")
    void logout(@RequestBody Map<String, String> logoutRequest, @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authHeader);
}

