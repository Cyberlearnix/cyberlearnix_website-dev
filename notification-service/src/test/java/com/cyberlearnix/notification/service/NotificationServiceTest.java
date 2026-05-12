package com.cyberlearnix.notification.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationService}.
 *
 * {@link JavaMailSender} is mocked so no real SMTP server is required.
 * A real (but empty) {@link MimeMessage} session is created so that
 * {@link org.springframework.mail.javamail.MimeMessageHelper} can set headers
 * without throwing a NullPointerException.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        // Provide a real (empty) MimeMessage so MimeMessageHelper can manipulate it
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void sendEmail_invokesSend_onMailSender() throws Exception {
        notificationService.sendEmail("recipient@example.com", "Test Subject", "<p>Hello</p>");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendContactEmail_delegatesToSendEmail() throws Exception {
        Map<String, Object> data = Map.of(
                "name", "Alice",
                "email", "alice@example.com",
                "message", "I have a question"
        );

        notificationService.sendContactEmail(data);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendEnrollmentAlert_delegatesToSendEmail() throws Exception {
        Map<String, Object> data = Map.of(
                "full_name", "Bob Smith",
                "email", "bob@example.com",
                "amount_paid", "4999"
        );

        notificationService.sendEnrollmentAlert(data);

        verify(mailSender).send(any(MimeMessage.class));
    }
}
