package com.cyberlearnix.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

/**
 * Feign client for notification-service in-app notification inbox.
 */
@FeignClient(name = "notification-service", url = "${services.notification-service.url:http://localhost:8084}")
public interface NotificationClient {

    @PostMapping("/api/notifications/inbox")
    List<Map<String, Object>> createInAppNotification(
            @RequestHeader("X-Internal-Service") String internalService,
            @RequestBody Map<String, Object> request);
}
