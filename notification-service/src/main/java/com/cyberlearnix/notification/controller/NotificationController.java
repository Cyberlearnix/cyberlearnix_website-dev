package com.cyberlearnix.notification.controller;

import com.cyberlearnix.notification.service.NotificationService;
import com.cyberlearnix.notification.dto.NotificationRequestDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
                    notificationService.sendAccountCredentials(requestDTO.getData());
                    break;
                case "send-admin-payment-alert":
                    notificationService.sendAdminPaymentAlert(requestDTO.getData());
                    break;
                case "send-credentials":
                    notificationService.sendCredentials(requestDTO.getData());
                    break;
                case "send-verified":
                    notificationService.sendVerified(requestDTO.getData());
                    break;
                case "send-rejected":
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
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
