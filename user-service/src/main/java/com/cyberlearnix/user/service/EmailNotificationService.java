package com.cyberlearnix.user.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    @Autowired
    private ResendService resendService;

    @Autowired
    private JavaMailSender mailSender;

    private static final String ADMIN_EMAIL = "cyberlearnix@gmail.com";
    private static final String AUTH_EMAIL = "cyberlearnixprivatelimited@gmail.com";

    @Async
    public void sendAdminInquiryNotification(String name, String email, String phone, String message) {
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

        String htmlContent = wrapInTemplate("New Contact Inquiry", content);

        boolean success = resendService.sendEmail(ADMIN_EMAIL, subject, htmlContent);
        if (!success) {
            sendViaSmtp(ADMIN_EMAIL, subject, htmlContent);
        }
    }

    private String wrapInTemplate(String heading, String content) {
        return "<div style=\"font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);\">"
                +
                "    <div style=\"background: linear-gradient(135deg, #002B5B 0%, #0057FF 100%); padding: 30px; text-align: center; color: white;\">"
                +
                "        <h1 style=\"margin: 0; font-size: 26px; letter-spacing: 1px; font-weight: 700;\">Cyberlearnix</h1>"
                +
                "        <p style=\"margin: 5px 0 0 0; opacity: 0.9; font-size: 14px; text-transform: uppercase; letter-spacing: 2px;\">"
                + heading + "</p>" +
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
                "    </div>" +
                "</div>";
    }

    private void sendViaSmtp(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom("Cyberlearnix <" + AUTH_EMAIL + ">");
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Admin notification SMTP failed: " + e.getMessage());
        }
    }

    @Async
    public void sendEmail(String to, String subject, String bodyHtml) {
        String wrapped = wrapInTemplate(subject, bodyHtml);
        boolean success = resendService.sendEmail(to, subject, wrapped);
        if (!success) {
            sendViaSmtp(to, subject, wrapped);
        }
    }
}
