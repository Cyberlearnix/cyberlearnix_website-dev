package com.cyberlearnix.enrollment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "notification-service", url = "http://localhost:8084")
public interface NotificationClient {

    @PostMapping("/api/notifications")
    Map<String, Object> sendNotification(@RequestParam("action") String action, @RequestBody Map<String, Object> request);
}
