package com.cyberlearnix.notification.controller;

import com.cyberlearnix.notification.service.NotificationService;
import com.cyberlearnix.notification.dto.NotificationRequestDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @PostMapping
    public ResponseEntity<?> handleNotification(@RequestParam String action, @RequestBody NotificationRequestDTO requestDTO)
            throws Exception {
        try {
            switch (action) {
                case "broadcast":
                    requireAdmin(action);
                    List<String> emails = requestDTO.getEmails();
                    String subject = requestDTO.getSubject();
                    String message = requestDTO.getMessage();
                    notificationService.sendBroadcast(emails, subject, message);
                    break;
                case "contact":
                    notificationService.sendContactEmail(requestDTO.getData());
                    break;
                case "send-confirmation":
                    notificationService.sendConfirmation(requestDTO.getData());
                    break;
                case "send-form-confirmation":
                    notificationService.sendFormConfirmation(requestDTO.getData());
                    break;
                case "send-account-credentials":
                    requireAdmin(action);
                    notificationService.sendAccountCredentials(requestDTO.getData());
                    break;
                case "send-admin-payment-alert":
                    requireAdmin(action);
                    notificationService.sendAdminPaymentAlert(requestDTO.getData());
                    break;
                case "send-credentials":
                    requireAdmin(action);
                    notificationService.sendCredentials(requestDTO.getData());
                    break;
                case "send-verified":
                    requireAdmin(action);
                    notificationService.sendVerified(requestDTO.getData());
                    break;
                case "send-rejected":
                    requireAdmin(action);
                    notificationService.sendRejected(requestDTO.getData());
                    break;
                case "invite":
                    notificationService.sendFormInvite(requestDTO.getData());
                    break;
                case "share-excel":
                    notificationService.sendShareResponses(requestDTO.getData());
                    break;
                default:
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid action"));
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Insufficient permissions for action: " + action));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private void requireAdmin(String action) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Admin role required for action: " + action);
        }
    }
}
