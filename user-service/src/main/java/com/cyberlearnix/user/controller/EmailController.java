package com.cyberlearnix.user.controller;

import com.cyberlearnix.user.service.ResendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class EmailController {

    @Autowired
    private ResendService resendService;

    @Autowired
    private JavaMailSender mailSender;

    @PostMapping("/send-form-receipt")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> sendFormReceipt(@RequestBody Map<String, Object> payload) {
        String recipientEmail = (String) payload.get("recipientEmail");
        String formTitle = (String) payload.get("formTitle");
        List<Map<String, String>> responses = (List<Map<String, String>>) payload.get("responses");

        String subject = "Submission Received: " + formTitle;
        String content = "<h3>Thank you for your submission!</h3>" +
                "<p>We have received your response for <strong>" + formTitle
                + "</strong>. Here is a copy of the details you provided:</p>" +
                buildStyledTable(responses) +
                "<p>Our team will review your submission and get back to you if necessary.</p>";

        String htmlContent = wrapInTemplate(subject, content);

        boolean success = resendService.sendEmail(recipientEmail, subject, htmlContent);
        if (!success) {
            success = sendViaSmtp(recipientEmail, subject, htmlContent);
        }

        return success ? ResponseEntity.ok(Map.of("message", "Receipt sent"))
                : ResponseEntity.internalServerError().body(Map.of("error", "Failed to send receipt"));
    }

    @PostMapping("/send-form-notification")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> sendFormNotification(@RequestBody Map<String, Object> payload) {
        String respondentEmail = (String) payload.get("respondentEmail");
        String formTitle = (String) payload.get("formTitle");
        List<Map<String, String>> answers = (List<Map<String, String>>) payload.get("answers");

        String adminEmail = "cyberlearnixprivatelimited@gmail.com";
        String subject = "New Form Submission: " + formTitle;
        String content = "<h3>New submission notification</h3>" +
                "<p>A new response has been submitted for the form: <strong>" + formTitle + "</strong></p>" +
                "<p><strong>Respondent:</strong> " + respondentEmail + "</p>" +
                buildStyledTable(answers);

        String htmlContent = wrapInTemplate(subject, content);

        boolean success = resendService.sendEmail(adminEmail, subject, htmlContent);
        if (!success) {
            success = sendViaSmtp(adminEmail, subject, htmlContent);
        }

        return success ? ResponseEntity.ok(Map.of("message", "Notification sent"))
                : ResponseEntity.internalServerError().body(Map.of("error", "Failed to send notification"));
    }

    @PostMapping("/send-reply")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> sendReply(@RequestBody Map<String, String> payload) {
        String to = payload.get("to");
        String subject = payload.get("subject");
        String message = payload.get("message");

        if (to == null || subject == null || message == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields: to, subject, message"));
        }

        String content = "<h3>Reply to your Inquiry</h3>" +
                "<div style='background: #f8fafc; padding: 20px; border-radius: 8px; border-left: 4px solid #0057FF; color: #2d3748; line-height: 1.8;'>"
                +
                message.replace("\n", "<br>") +
                "</div>" +
                "<p style='margin-top: 20px;'>If you have any further questions, please don't hesitate to reach out.</p>";

        String htmlContent = wrapInTemplate(subject, content);

        boolean success = resendService.sendEmail(to, subject, htmlContent);
        if (!success) {
            success = sendViaSmtp(to, subject, htmlContent);
        }

        return success ? ResponseEntity.ok(Map.of("success", true, "message", "Email sent"))
                : ResponseEntity.internalServerError().body(Map.of("error", "Failed to send email"));
    }

    @PostMapping("/send-email")
    public ResponseEntity<?> sendEmail(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        String email = payload.get("email");
        String phone = payload.get("phone");
        String message = payload.get("message");

        String adminEmail = "cyberlearnixprivatelimited@gmail.com";
        String subject = "New Inquiry from " + name;

        String content = "<h3>Website Inquiry Received</h3>" +
                "<p>Hey Admin, you have a new message from the contact form:</p>" +
                "<table style='width: 100%; border-collapse: collapse; margin-bottom: 20px;'>" +
                "<tr><td style='padding: 8px 0; font-weight: bold; width: 100px;'>Name:</td><td style='padding: 8px 0;'>"
                + name + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Email:</td><td style='padding: 8px 0;'>" + email
                + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Phone:</td><td style='padding: 8px 0;'>" + phone
                + "</td></tr>" +
                "</table>" +
                "<p><strong>Message:</strong></p>" +
                "<div style='background: #f7fafc; padding: 15px; border-radius: 6px; border: 1px solid #e2e8f0; font-style: italic;'>"
                + message + "</div>";

        String htmlContent = wrapInTemplate(subject, content);

        boolean success = resendService.sendEmail(adminEmail, subject, htmlContent);
        if (!success) {
            success = sendViaSmtp(adminEmail, subject, htmlContent);
        }

        return success ? ResponseEntity.ok(Map.of("success", true, "message", "Inquiry sent"))
                : ResponseEntity.internalServerError().body(Map.of("error", "Failed to send inquiry"));
    }

    @PostMapping("/send-enrollment-credentials")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> sendEnrollmentCredentials(@RequestBody Map<String, String> payload) {
        String studentEmail = payload.get("studentEmail");
        String studentName = payload.get("studentName");
        String temporaryPassword = payload.get("temporaryPassword");
        String courseName = payload.get("courseName");
        String loginUrl = payload.get("loginUrl");

        if (studentEmail == null || temporaryPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "studentEmail and temporaryPassword are required"));
        }

        String subject = "Your Cyberlearnix Account & Course Access";
        String content = "<h3>Welcome to Cyberlearnix, " + (studentName != null ? studentName : "Student") + "!</h3>" +
                "<p>Your account has been created and you have been enrolled in <strong>" + courseName + "</strong>.</p>" +
                "<p>Here are your login credentials:</p>" +
                "<div style='background:#f0f4ff;padding:20px;border-radius:8px;margin:20px 0;border-left:4px solid #0057FF;'>" +
                "<p style='margin:4px 0'><strong>Email:</strong> " + studentEmail + "</p>" +
                "<p style='margin:4px 0'><strong>Temporary Password:</strong> <code style='background:#fff;padding:2px 8px;border-radius:4px;font-size:15px;letter-spacing:1px'>" + temporaryPassword + "</code></p>" +
                "</div>" +
                "<p>Please <a href='" + (loginUrl != null ? loginUrl : "https://cyberlearnix.com/login") + "' style='color:#0057FF;font-weight:600'>log in here</a> and change your password after your first login.</p>" +
                "<p style='color:#718096;font-size:13px'>If you have any issues, reply to this email or contact our support team.</p>";

        String htmlContent = wrapInTemplate(subject, content);

        boolean success = resendService.sendEmail(studentEmail, subject, htmlContent);
        if (!success) {
            success = sendViaSmtp(studentEmail, subject, htmlContent);
        }

        return success
                ? ResponseEntity.ok(Map.of("success", true, "message", "Credentials email sent"))
                : ResponseEntity.internalServerError().body(Map.of("error", "Failed to send credentials email"));
    }

    private String wrapInTemplate(String title, String content) {
        return "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);\">"
                +
                "    <div style=\"background: linear-gradient(135deg, #002B5B 0%, #0057FF 100%); padding: 30px; text-align: center; color: white;\">"
                +
                "        <h1 style=\"margin: 0; font-size: 26px; letter-spacing: 1px; font-weight: 700;\">Cyberlearnix</h1>"
                +
                "        <p style=\"margin: 5px 0 0 0; opacity: 0.9; font-size: 14px; text-transform: uppercase; letter-spacing: 2px;\">Empowering Minds, Securing Futures</p>"
                +
                "    </div>" +
                "    <div style=\"padding: 40px; background: white; color: #2d3748; line-height: 1.6;\">" +
                "        " + content + "" +
                "    </div>" +
                "    <div style=\"background: #f8fafc; padding: 25px; text-align: center; border-top: 1px solid #e2e8f0; color: #718096;\">"
                +
                "        <p style=\"margin: 0; font-size: 13px; font-weight: 600;\">&copy; 2026 Cyberlearnix Private Limited. All rights reserved.</p>"
                +
                "        <p style=\"margin: 8px 0 0 0; font-size: 12px;\">Hyderabad, Telangana, India | connect@cyberlearnix.com</p>"
                +
                "        <div style=\"margin-top: 15px;\">" +
                "            <a href=\"#\" style=\"color: #0057FF; text-decoration: none; margin: 0 10px; font-size: 12px;\">Website</a>"
                +
                "            <a href=\"#\" style=\"color: #0057FF; text-decoration: none; margin: 0 10px; font-size: 12px;\">Courses</a>"
                +
                "            <a href=\"#\" style=\"color: #0057FF; text-decoration: none; margin: 0 10px; font-size: 12px;\">Support</a>"
                +
                "        </div>" +
                "    </div>" +
                "</div>";
    }

    private String buildStyledTable(List<Map<String, String>> rows) {
        StringBuilder html = new StringBuilder();
        html.append("<div style='margin: 20px 0; border: 1px solid #edf2f7; border-radius: 8px; overflow: hidden;'>");
        html.append("<table style='width: 100%; border-collapse: collapse; background: white;'>");
        html.append(
                "<tr style='background-color: #f7fafc;'><th style='padding: 12px 15px; text-align: left; border-bottom: 2px solid #edf2f7; color: #4a5568; font-size: 13px; text-transform: uppercase;'>Question</th><th style='padding: 12px 15px; text-align: left; border-bottom: 2px solid #edf2f7; color: #4a5568; font-size: 13px; text-transform: uppercase;'>Answer</th></tr>");

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            String question = row.get("question") != null ? row.get("question") : row.get("label");
            String answer = row.get("answer") != null ? row.get("answer") : row.get("value");
            String bgColor = (i % 2 == 1) ? "#fcfcfc" : "white";

            html.append("<tr style='background-color: ").append(bgColor).append(";'>");
            html.append(
                    "<td style='padding: 12px 15px; border-bottom: 1px solid #edf2f7; color: #718096; font-size: 14px; font-weight: 500;'>")
                    .append(question).append("</td>");
            html.append(
                    "<td style='padding: 12px 15px; border-bottom: 1px solid #edf2f7; color: #2d3748; font-size: 14px;'>")
                    .append(answer).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");
        html.append("</div>");
        return html.toString();
    }

    private boolean sendViaSmtp(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            // Must match the authenticated account for delivery success on Gmail
            helper.setFrom("Cyberlearnix <cyberlearnixprivatelimited@gmail.com>");
            helper.setText(htmlContent, true);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            System.err.println("SMTP email delivery failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
