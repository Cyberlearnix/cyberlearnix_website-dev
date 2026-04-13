package com.cyberlearnix.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "user-service-admin", url = "${services.user-service.url:http://localhost:8081}")
public interface UserServiceClient {

    @GetMapping("/api/users")
    List<Map<String, Object>> getAllUsers(@RequestParam(required = false) String role,
                                          @RequestHeader("Authorization") String auth);

    @GetMapping("/api/users/{id}")
    Map<String, Object> getUserById(@PathVariable("id") String id,
                                     @RequestHeader("Authorization") String auth);

    @PutMapping("/api/users/{id}/status")
    Map<String, Object> updateUserStatus(@PathVariable("id") String id,
                                          @RequestBody Map<String, Boolean> statusRequest,
                                          @RequestHeader("Authorization") String auth);

    @DeleteMapping("/api/users/{id}")
    Map<String, Object> deleteUser(@PathVariable("id") String id,
                                    @RequestHeader("Authorization") String auth);

    @PutMapping("/api/users/{id}")
    Map<String, Object> updateUser(@PathVariable("id") String id,
                                    @RequestBody Map<String, String> profileRequest,
                                    @RequestHeader("Authorization") String auth);

    @GetMapping("/api/admin/stats/users")
    Map<String, Object> getUserStats(@RequestHeader("Authorization") String auth);
}
