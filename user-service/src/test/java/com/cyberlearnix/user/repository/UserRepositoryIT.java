package com.cyberlearnix.user.repository;

import com.cyberlearnix.shared.entity.user.User;
import com.cyberlearnix.shared.repository.user.UserRepository;
import com.cyberlearnix.user.service.EmailNotificationService;
import com.cyberlearnix.user.service.OtpService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration / JPA slice tests for {@link UserRepository}.
 *
 * Uses a full Spring Boot context with the "test" profile so the H2
 * in-memory database (MODE=PostgreSQL) is used instead of the real PostgreSQL.
 * Each test runs inside a transaction that is rolled back automatically.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class UserRepositoryIT {

    /** Mock out OtpService so the context loads without a real Redis connection. */
    @MockBean
    private OtpService otpService;

    /** Mock out EmailNotificationService so the context loads without JavaMailSender. */
    @MockBean
    private EmailNotificationService emailNotificationService;

    @Autowired
    private UserRepository userRepository;

    private User createUser(String email, String role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$dummy-hash-placeholder-value-here");
        user.setRole(role);
        return userRepository.save(user);
    }

    @Test
    void findByEmail_returnsUser_whenEmailExists() {
        createUser("alice@example.com", "student");

        Optional<User> result = userRepository.findByEmail("alice@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findByEmail_returnsEmpty_whenEmailDoesNotExist() {
        Optional<User> result = userRepository.findByEmail("nobody@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void countByRoleIgnoreCase_returnsCorrectCount() {
        createUser("s1@example.com", "student");
        createUser("s2@example.com", "STUDENT");
        createUser("t1@example.com", "teacher");

        long studentCount = userRepository.countByRoleIgnoreCase("student");

        assertThat(studentCount).isEqualTo(2);
    }
}
