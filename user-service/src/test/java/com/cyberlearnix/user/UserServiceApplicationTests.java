package com.cyberlearnix.user;

import com.cyberlearnix.user.service.EmailNotificationService;
import com.cyberlearnix.user.service.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class UserServiceApplicationTests {

    /** Mock Redis-backed OtpService — Redis is excluded in test profile */
    @MockBean
    private OtpService otpService;

    /** Mock mail-dependent EmailNotificationService — Mail is excluded in test profile */
    @MockBean
    private EmailNotificationService emailNotificationService;

    /** Mock JavaMailSender directly (some beans autowire it independently) */
    @MockBean
    private JavaMailSender mailSender;

    @Test
    void contextLoads() {
    }
}
