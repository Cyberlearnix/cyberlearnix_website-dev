package com.cyberlearnix.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class NotificationServiceApplicationTests {

    /** Mock JavaMailSender — MailSenderAutoConfiguration is excluded in test profile */
    @MockBean
    private JavaMailSender mailSender;

    @Test
    void contextLoads() {
    }
}
