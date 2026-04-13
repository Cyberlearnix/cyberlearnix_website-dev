package com.cyberlearnix.enrollment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "user-service", url = "${services.user-service.url:http://localhost:8081}")
public interface UserClient {

    @PostMapping("/api/auth/register")
    Map<String, Object> registerUser(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> registerRequest);
}

