package com.cyberlearnix.admin.config;

import com.cyberlearnix.shared.entity.user.ActivityLog;
import com.cyberlearnix.shared.repository.ActivityLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminAuditInterceptor implements HandlerInterceptor {

    private final ActivityLogRepository activityLogRepository;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String method = request.getMethod();
        if (method.equals("POST") || method.equals("PUT") || method.equals("DELETE") || method.equals("PATCH")) {
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                logAction(request);
            }
        }
    }

    private void logAction(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = (auth != null && auth.getPrincipal() != null) ? auth.getPrincipal().toString() : "SYSTEM";
        
        ActivityLog log = new ActivityLog();
        log.setUserId(userId);
        log.setEventType("ADMIN_ACTION");
        log.setDescription(request.getMethod() + " " + request.getRequestURI());
        log.setMetadata(Map.of(
            "ip", request.getRemoteAddr(),
            "userAgent", request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "unknown"
        ));
        
        activityLogRepository.save(log);
    }
}
