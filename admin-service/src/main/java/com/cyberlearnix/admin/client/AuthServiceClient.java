package com.cyberlearnix.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "user-service", url = "http://127.0.0.1:8081/api/auth")
public interface AuthServiceClient {

    @PostMapping("/login")
    Map<String, Object> login(@RequestBody Map<String, String> loginRequest);

    @PostMapping("/logout")
    void logout(@RequestBody Map<String, String> logoutRequest, @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authHeader);
}
