package com.cyberlearnix.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.student-portal-url:http://localhost:8080/student/}")
    private String studentPortalUrl;

    public void sendEmail(String to, String subject, String htmlContent) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom("Cyberlearnix <cyberlearnixprivatelimited@gmail.com>");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    public void sendContactEmail(Map<String, Object> data) throws Exception {
        String subject = "New Website Inquiry Received";
        String content = "<h3>Hello Admin,</h3>" +
                "<p>A new visitor has reached out via the contact form on your website:</p>" +
                "<div style='background: #f8fafc; padding: 20px; border-radius: 12px; border: 1px solid #e2e8f0; margin: 20px 0;'>"
                +
                "<p style='margin: 0 0 10px 0;'><strong>Name:</strong> " + (String) data.get("name") + "</p>" +
                "<p style='margin: 0 0 10px 0;'><strong>Email:</strong> " + (String) data.get("email") + "</p>" +
                "<p style='margin: 0;'><strong>Message:</strong></p>" +
                "<p style='background: white; padding: 15px; border-radius: 6px; border: 1px solid #edf2f7; font-style: italic; color: #4a5568;'>"
                + (String) data.get("message") + "</p>" +
                "</div>";

        sendEmail("cyberlearnix@gmail.com", subject, wrapInTemplate("New Contact Inquiry", content));
    }

    public void sendEnrollmentAlert(Map<String, Object> data) throws Exception {
        String subject = "🚨 Action Required: New Enrollment Alert";
        String content = "<h3>Alert: New Enrollment Submission</h3>" +
                "<p>A student has just applied for a course. Please review the payment details in the admin panel:</p>"
                +
                "<div style='background: #fef2f2; padding: 20px; border-radius: 12px; border: 1px solid #fee2e2; color: #991b1b;'>"
                +
                "<p style='margin: 0 0 10px 0;'><strong>Student:</strong> " + (String) data.get("full_name") + "</p>" +
                "<p style='margin: 0 0 10px 0;'><strong>Email:</strong> " + (String) data.get("email") + "</p>" +
                "<p style='margin: 0; font-size: 18px;'><strong>Amount Paid:</strong> ₹" + (String) data.get("amount_paid")
                + "</p>" +
                "</div>";

        sendEmail("cyberlearnix@gmail.com", subject, wrapInTemplate("Enrollment Alert", content));
    }

    public void sendConfirmation(Map<String, Object> data) throws Exception {
        String subject = "Enrollment Received: " + (String) data.get("courseNames");
        String content = "<h3>Application Successfully Submitted!</h3>" +
                "<p>Dear <strong>" + (String) data.get("studentName") + "</strong>,</p>" +
                "<p>We have received your enrollment application for the course: <strong>" + (String) data.get("courseNames")
                + "</strong>.</p>" +
                "<div style='background: #ecfdf5; padding: 20px; border-radius: 12px; border: 1px solid #d1fae5; color: #065f46;'>"
                +
                "Our team is currently verifying your payment details. Once verified, you will receive your course access credentials via email."
                +
                "</div>" +
                "<p style='margin-top: 20px;'>Thank you for choosing Cyberlearnix!</p>";

        sendEmail((String) data.get("studentEmail"), subject, wrapInTemplate("Application Received", content));
    }

    public void sendCredentials(Map<String, Object> data) throws Exception {
        String subject = "🎓 Welcome aboard! Your Course Access is Ready";
        String content = "<h3>Let's start your learning journey!</h3>" +
                "<p>Hello <strong>" + (String) data.get("studentName") + "</strong>,</p>" +
                "<p>Your payment has been verified, and your access to the Cyberlearnix portal is now active. Please use the following temporary credentials to sign in:</p>"
                +
                "<div style='background: #f8fafc; padding: 25px; border-radius: 12px; border: 1px solid #e2e8f0; margin: 25px 0;'>"
                +
                "<p style='margin: 0 0 10px 0;'><strong>Login Portal:</strong> <a href=\"" + studentPortalUrl + "\">" + studentPortalUrl + "</a></p>"
                +
                "<p style='margin: 0 0 10px 0;'><strong>Username:</strong> " + (String) data.get("studentEmail") + "</p>" +
                "<p style='margin: 0;'><strong>Temporary Password:</strong> <code style='background: #edf2f7; padding: 2px 6px; border-radius: 4px; font-weight: bold;'>"
                + (String) data.get("temporaryPassword") + "</code></p>" +
                "</div>" +
                "<div style='text-align: center; margin: 30px 0;'>" +
                "    <a href=\"" + studentPortalUrl + "\" style=\"background: #0057FF; color: white; padding: 14px 30px; border-radius: 8px; text-decoration: none; font-weight: 700; display: inline-block;\">Access Learning Portal</a>"
                +
                "</div>" +
                "<p style='font-size: 14px; color: #718096;'><em>Note: You will be prompted to set a permanent password after your first login.</em></p>";

        sendEmail((String) data.get("studentEmail"), subject, wrapInTemplate("Access Credentials", content));
    }

    public void sendAccountCredentials(Map<String, Object> data) throws Exception {
        String subject = "🔑 Your Cyberlearnix Student Credentials";
        String content = "<h3>Welcome to Cyberlearnix!</h3>" +
                "<p>Your account has been created for the course: <strong>" + (String) data.get("courseTitle") + "</strong>.</p>" +
                "<p>You can now log in using the following credentials:</p>" +
                "<div style='background: #f8fafc; padding: 20px; border-radius: 12px; border: 1px solid #e2e8f0; margin: 20px 0;'>" +
                "<p><strong>Login URL:</strong> <a href=\"" + studentPortalUrl + "\">" + studentPortalUrl + "</a></p>" +
                "<p><strong>Email:</strong> " + (String) data.get("email") + "</p>" +
                "<p><strong>Temporary Password:</strong> <code style='background: #eee; padding: 4px; border-radius: 4px;'>" + (String) data.get("password") + "</code></p>" +
                "</div>" +
                "<p>Please change your password after your first login.</p>" +
                "<div style='text-align: center; margin: 25px 0;'>" +
                "    <a href=\"" + studentPortalUrl + "\" style=\"background: #0057FF; color: white; padding: 14px 30px; border-radius: 8px; text-decoration: none; font-weight: 700; display: inline-block;\">Go to Student Portal</a>" +
                "</div>";

        sendEmail((String) data.get("email"), subject, wrapInTemplate("Credentials Issued", content));
    }

    public void sendAdminPaymentAlert(Map<String, Object> data) throws Exception {
        String subject = "💰 New Payment Received: " + (String) data.get("formTitle");
        String content = "<h3>New Enrollment Payment!</h3>" +
                "<p>A student has completed a payment. Please verify the UTR in the admin dashboard.</p>" +
                "<div style='background: #f0fdf4; padding: 20px; border-radius: 12px; border: 1px solid #dcfce7; margin: 20px 0;'>" +
                "<p><strong>Form:</strong> " + (String) data.get("formTitle") + "</p>" +
                "<p><strong>Student:</strong> " + (String) data.get("studentEmail") + "</p>" +
                "<p><strong>UTR/TxnId:</strong> " + (String) data.get("utr") + "</p>" +
                "<p><strong>Amount:</strong> ₹" + data.get("amount") + "</p>" +
                "</div>" +
                "<p><a href=\"https://cyberlearnix.com/admin/enrollments\">Go to Admin Dashboard</a></p>";

        sendEmail("cyberlearnix@gmail.com", subject, wrapInTemplate("Payment Alert", content));
    }

    public void sendVerified(Map<String, Object> data) throws Exception {
        String subject = "✅ Enrollment Verified: " + (String) data.get("courseName");
        String content = "<h3>Payment Successfully Verified!</h3>" +
                "<p>Hello <strong>" + (String) data.get("studentName") + "</strong>,</p>" +
                "<p>We have successfully verified your payment for <strong>" + (String) data.get("courseName")
                + "</strong>. You now have full access to your course materials.</p>" +
                "<div style='text-align: center; margin: 30px 0;'>" +
                "    <a href=\"" + studentPortalUrl + "\" style=\"background: #0057FF; color: white; padding: 14px 30px; border-radius: 8px; text-decoration: none; font-weight: 700; display: inline-block;\">Go to Student Portal</a>"
                +
                "</div>";

        sendEmail((String) data.get("studentEmail"), subject, wrapInTemplate("Payment Verified", content));
    }

    public void sendRejected(Map<String, Object> data) throws Exception {
        String subject = "❌ Verification Update: " + (String) data.get("courseName");
        String content = "<h3>Payment Verification Unsuccessful</h3>" +
                "<p>Hello <strong>" + (String) data.get("studentName") + "</strong>,</p>" +
                "<p>We were unable to verify your payment for <strong>" + (String) data.get("courseName")
                + "</strong> due to the following reason:</p>" +
                "<div style='background: #fff5f5; padding: 20px; border-radius: 12px; border: 1px solid #fed7d7; color: #c53030; margin: 20px 0;'>"
                +
                "<strong>Reason:</strong> " + (String) data.get("rejectionReason") +
                "</div>" +
                "<p>Please contact our support team or try re-submitting your payment details with the correct proof of transaction.</p>";

        sendEmail((String) data.get("studentEmail"), subject, wrapInTemplate("Verification Update", content));
    }

    public void sendFormConfirmation(Map<String, Object> data) throws Exception {
        String recipientEmail = (String) data.get("recipientEmail");
        String formTitle = (String) data.get("formTitle");
        String responsesJson = (String) data.get("responses");

        String subject = "Response Received: " + formTitle;
        
        StringBuilder responseTable = new StringBuilder();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> responses = mapper.readValue(responsesJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            
            responseTable.append("<table style='width: 100%; border-collapse: collapse; margin-top: 20px;'>");
            for (Map.Entry<String, Object> entry : responses.entrySet()) {
                responseTable.append("<tr>")
                    .append("<td style='padding: 10px; border-bottom: 1px solid #eee; font-weight: 600; width: 40%;'>").append(entry.getKey()).append("</td>")
                    .append("<td style='padding: 10px; border-bottom: 1px solid #eee;'>").append(entry.getValue()).append("</td>")
                    .append("</tr>");
            }
            responseTable.append("</table>");
        } catch (Exception e) {
            responseTable.append("<p>Details recorded successfully.</p>");
        }

        String content = "<h3>Thank You for Your Submission!</h3>" +
                "<p>We have received your response for the form: <strong>" + formTitle + "</strong></p>" +
                "<div style='background: #f8fafc; padding: 20px; border-radius: 12px; border: 1px solid #e2e8f0; margin: 20px 0;'>" +
                "<p><strong>Summary of your responses:</strong></p>" +
                responseTable.toString() +
                "</div>" +
                "<p>Our team will review your submission and get back to you if necessary.</p>";

        sendEmail(recipientEmail, subject, wrapInTemplate("Submission Confirmed", content));
    }

    public void sendFormInvite(Map<String, Object> data) throws Exception {
        String subject = "Collaboration Invite: " + data.get("formTitle");
        Object emailsObj = data.get("emails");
        java.util.List<String> emails;
        if (emailsObj instanceof java.util.List) {
            emails = (java.util.List<String>) emailsObj;
        } else if (emailsObj instanceof String) {
            emails = java.util.Arrays.stream(((String) emailsObj).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } else {
            throw new RuntimeException("Invalid email format for invitations");
        }
        String formLink = (String) data.get("formLink");
        String formTitle = (String) data.get("formTitle");
        String message = (String) data.get("message");

        String content = "<h3>You've Been Invited to Collaborate</h3>" +
                "<p>Hello,</p>" +
                "<p>You are invited to fill out or collaborate on the form: <strong>" + formTitle + "</strong></p>" +
                (message != null && !message.isEmpty() ? "<div style='background: #f1f5f9; padding: 15px; border-radius: 8px; margin: 20px 0; border: 1px solid #e2e8f0; font-style: italic;'>" + message + "</div>" : "") +
                "<div style='text-align: center; margin: 30px 0;'>" +
                "    <a href=\"" + formLink + "\" style=\"background: #0057FF; color: white; padding: 14px 30px; border-radius: 8px; text-decoration: none; font-weight: 700; display: inline-block;\">Open Form</a>" +
                "</div>";

        String htmlContent = wrapInTemplate("Form Invitation", content);

        for (String email : emails) {
            try {
                sendEmail(email, subject, htmlContent);
            } catch (Exception e) {
                System.err.println("Failed to send invite to " + email + ": " + e.getMessage());
            }
        }
    }

    public void sendShareResponses(Map<String, Object> data) throws Exception {
        String recipientEmail = (String) data.get("recipientEmail");
        String formTitle = (String) data.get("formTitle");
        String fileData = (String) data.get("fileData"); // Base64 encoded Excel

        String subject = "Shared Responses: " + formTitle;
        String content = "<h3>Form Responses Shared With You</h3>" +
                "<p>Please find the attached Excel file containing the responses for the form: <strong>" + formTitle + "</strong></p>" +
                "<p>This file includes all submissions collected up to this moment.</p>";

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom("Cyberlearnix <cyberlearnixprivatelimited@gmail.com>");
        helper.setTo(recipientEmail);
        helper.setSubject(subject);
        helper.setText(wrapInTemplate("Shared Responses", content), true);

        // Add attachment
        byte[] decodedBytes = java.util.Base64.getDecoder().decode(fileData);
        helper.addAttachment(formTitle.replaceAll("[^a-zA-Z0-9]", "_") + "_Responses.xlsx",
                new org.springframework.core.io.ByteArrayResource(decodedBytes));

        mailSender.send(message);
    }

    public void sendBroadcast(List<String> emails, String subject, String message) throws Exception {
        String htmlContent = wrapInTemplate("Broadcast Message",
                "<div style='line-height: 1.6; color: #334155;'>" + message + "</div>");

        for (String email : emails) {
            try {
                sendEmail(email, subject, htmlContent);
            } catch (Exception e) {
                System.err.println("Failed to send broadcast to " + email + ": " + e.getMessage());
            }
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
}
