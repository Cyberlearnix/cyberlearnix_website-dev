package com.cyberlearnix.user.security;

import com.cyberlearnix.shared.annotation.AuditLog;
import com.cyberlearnix.shared.entity.user.AuthAudit;
import com.cyberlearnix.shared.repository.user.AuthAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Aspect to intercept and audit authentication-related method calls
 * Logs successful and failed authentication attempts with IP, User-Agent, etc.
 */
@Slf4j
@Aspect
@Component
public class AuthAuditAspect {

    @Autowired
    private AuthAuditRepository authAuditRepository;

    /**
     * Log successful login/token refresh operations
     */
    @AfterReturning(pointcut = "@annotation(auditLog)", returning = "result")
    public void auditSuccessfulAuth(JoinPoint joinPoint, AuditLog auditLog, Object result) {
        try {
            if (result instanceof Map) {
                Map<String, Object> resultMap = (Map<String, Object>) result;
                String email = extractEmail(joinPoint);
                String userId = null;

                if (resultMap.containsKey("user") && resultMap.get("user") instanceof Map) {
                    Map<String, Object> userMap = (Map<String, Object>) resultMap.get("user");
                    userId = (String) userMap.get("id");
                }

                AuthAudit audit = AuthAudit.builder()
                        .email(email)
                        .action(auditLog.action())
                        .userId(userId)
                        .success(true)
                        .ipAddress(getClientIp())
                        .userAgent(getUserAgent())
                        .timestamp(LocalDateTime.now())
                        .build();

                authAuditRepository.save(audit);
                log.info("Audit: {} - Email: {} - IP: {}", auditLog.action(), email, getClientIp());
            }
        } catch (Exception e) {
            log.error("Error in audit logging for successful auth: ", e);
        }
    }

    /**
     * Log failed authentication attempts
     */
    @AfterThrowing(pointcut = "@annotation(auditLog)", throwing = "exception")
    public void auditFailedAuth(JoinPoint joinPoint, AuditLog auditLog, Exception exception) {
        try {
            String email = extractEmail(joinPoint);

            AuthAudit audit = AuthAudit.builder()
                    .email(email)
                    .action(auditLog.action())
                    .success(false)
                    .reason(exception.getMessage())
                    .ipAddress(getClientIp())
                    .userAgent(getUserAgent())
                    .timestamp(LocalDateTime.now())
                    .build();

            authAuditRepository.save(audit);
            log.warn("Audit: {} FAILED - Email: {} - Reason: {} - IP: {}", 
                    auditLog.action(), email, exception.getMessage(), getClientIp());
        } catch (Exception e) {
            log.error("Error in audit logging for failed auth: ", e);
        }
    }

    /**
     * Extract email from method arguments
     */
    private String extractEmail(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            if (args[0] instanceof String) {
                return (String) args[0]; // First argument is email
            } else if (args[0] instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) args[0];
                if (map.containsKey("email")) {
                    return (String) map.get("email");
                }
            }
        }
        return "UNKNOWN";
    }

    /**
     * Get client IP address, considering proxy headers
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                String forwardedFor = request.getHeader("X-Forwarded-For");
                if (forwardedFor != null && !forwardedFor.isEmpty()) {
                    return forwardedFor.split(",")[0].trim();
                }

                String clientIp = request.getHeader("X-Client-IP");
                if (clientIp != null && !clientIp.isEmpty()) {
                    return clientIp;
                }

                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.warn("Error getting client IP: ", e);
        }
        return "UNKNOWN";
    }

    /**
     * Get User-Agent from request
     */
    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String userAgent = request.getHeader("User-Agent");
                return userAgent != null ? userAgent.substring(0, Math.min(500, userAgent.length())) : "UNKNOWN";
            }
        } catch (Exception e) {
            log.warn("Error getting User-Agent: ", e);
        }
        return "UNKNOWN";
    }
}
