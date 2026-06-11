package com.cyberlearnix.user.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {
    private static final int OTP_LENGTH = 6;
    private static final long OTP_EXPIRATION_MINUTES = 5;
    private static final String REDIS_OTP_PREFIX = "otp:";
    private static final String REDIS_RATE_LIMIT_PREFIX = "otp_limit:";
    private static final int MAX_OTP_REQUESTS = 3;
    private static final long OTP_RATE_WINDOW_MINUTES = 15;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JavaMailSender mailSender;

    public boolean isOtpRateLimited(String email) {
        // Rate limiting disabled
        return false;
    }

    public String generateAndSendOtp(String email) throws Exception {
        String otp = generateOtp();
        String sessionId = java.util.UUID.randomUUID().toString();
        
        // Store in Redis with TTL
        String key = REDIS_OTP_PREFIX + email.toLowerCase().trim();
        String value = otp + ":" + sessionId;
        redisTemplate.opsForValue().set(key, value, OTP_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        
        System.out.println("OTP GENERATED for " + email + ": " + otp + " (Session: " + sessionId + ")");
        sendEmail(email, otp);
        return sessionId;
    }

    public boolean verifyOtp(String email, String otp, String sessionId) {
        String key = REDIS_OTP_PREFIX + email.toLowerCase().trim();
        String storedValue = redisTemplate.opsForValue().get(key);
        
        if (storedValue == null) {
            System.out.println("VERIFICATION FAILED: No OTP found in Redis for " + email);
            return false;
        }

        String[] parts = storedValue.split(":");
        if (parts.length != 2) {
            System.out.println("VERIFICATION FAILED: Invalid data format in Redis for " + email);
            return false;
        }

        String storedOtp = parts[0];
        String storedSessionId = parts[1];

        boolean matches = storedOtp.equals(otp) && storedSessionId.equals(sessionId);
        
        if (matches) {
            System.out.println("VERIFICATION SUCCESS for " + email);
            redisTemplate.delete(key); // Remove after successful verification
        } else {
            System.out.println("VERIFICATION FAILED: Mismatch for " + email + ". Stored: " + storedOtp + ":" + storedSessionId + ", Input: " + otp + ":" + sessionId);
        }
        return matches;
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(random.nextInt(10)); // digits 0-9
        }
        return sb.toString();
    }

    private void sendEmail(String to, String otp) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(to);
        helper.setSubject("Your OTP Verification Code - Cyberlearnix");
        helper.setFrom("Cyberlearnix <cyberlearnixprivatelimited@gmail.com>");

        String htmlContent = wrapInTemplate("OTP Verification",
                "<h2 style='text-align: center; color: #0057FF; margin-bottom: 25px;'>Verification Code</h2>" +
                        "<p>Hello,</p>" +
                        "<p>To complete your sign-in or verification, please use the following One-Time Password (OTP):</p>"
                        +
                        "<div style='text-align: center; margin: 30px 0;'>" +
                        "    <span style='background: #f1f5f9; color: #002B5B; font-size: 36px; font-weight: 800; padding: 15px 40px; border-radius: 12px; letter-spacing: 12px; border: 2px solid #e2e8f0; display: inline-block;'>"
                        + otp + "</span>" +
                        "</div>" +
                        "<p style='text-align: center; color: #718096; font-size: 14px;'>This code is valid for <strong>"
                        + OTP_EXPIRATION_MINUTES + " minutes</strong>. Please do not share this code with anyone.</p>" +
                        "<hr style='border: none; border-top: 1px solid #edf2f7; margin: 30px 0;' />" +
                        "<p style='font-size: 13px; color: #a0aec0;'>If you did not request this verification code, please ignore this email or contact support if you have concerns about your account security.</p>");

        helper.setText(htmlContent, true);
        mailSender.send(message);
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
