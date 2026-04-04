package com.cyberlearnix.form.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Map;

@FeignClient(name = "user-service", url = "http://localhost:8081")
public interface UserClient {
    
    @GetMapping("/api/users/{id}")
    Map<String, Object> getUserById(@PathVariable("id") String id);
}
