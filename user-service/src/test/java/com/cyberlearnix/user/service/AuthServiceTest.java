package com.cyberlearnix.user.service;

import com.cyberlearnix.shared.entity.user.User;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.user.BlacklistedTokenRepository;
import com.cyberlearnix.shared.repository.user.PasswordResetTokenRepository;
import com.cyberlearnix.shared.repository.user.RefreshTokenRepository;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import com.cyberlearnix.shared.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 *
 * Dependencies are mocked with Mockito so no Spring context or database is needed.
 * Cases that don't reach generateToken() work without a jwtSecret; the one success-path
 * test sets a 512-bit dummy secret via ReflectionTestUtils.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private BlacklistedTokenRepository blacklistedTokenRepository;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void injectSecrets() {
        // Provide a 512-bit dummy secret so generateToken() works if called
        ReflectionTestUtils.setField(authService, "jwtSecret",
                "test-secret-key-for-unit-tests-at-least-64-chars-to-satisfy-hmac-sha512-minimum-length!!!");
        ReflectionTestUtils.setField(authService, "jwtExpiration", 3_600_000L);
        ReflectionTestUtils.setField(authService, "refreshExpiration", 7_200_000L);
    }

    // ── register ────────────────────────────────────────────────────────────────

    @Test
    void register_shouldCreateUserAndProfile_whenEmailIsNew() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        User saved = new User();
        saved.setId("uuid-123");
        saved.setEmail("new@example.com");
        saved.setRole("student");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(new UserProfile());

        Map<String, Object> result = authService.register("new@example.com", "password123", "student");

        assertThat(result).containsKey("id").containsKey("email").containsKey("role");
        assertThat(result).containsEntry("email", "new@example.com");
        verify(userRepository).save(any(User.class));
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void register_shouldThrow_whenEmailIsAlreadyRegistered() {
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.register("existing@example.com", "pass", "student"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
    }

    // ── login ────────────────────────────────────────────────────────────────────

    @Test
    void login_shouldThrow_whenUserDoesNotExist() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost@example.com", "anyPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_shouldThrow_whenAccountIsLocked() {
        User locked = new User();
        locked.setEmail("locked@example.com");
        locked.setPasswordHash("$2a$10$irrelevant");
        locked.setFailedAttempts(5); // at MAX_FAILED_ATTEMPTS

        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> authService.login("locked@example.com", "anyPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void login_shouldIncrementFailedAttempts_whenPasswordIsWrong() {
        User user = new User();
        user.setEmail("user@example.com");
        // BCrypt hash of "correctPass" — wrong hash so passwordEncoder.matches returns false
        user.setPasswordHash("$2a$10$00000000000000000000000000000000000000000000000000000000");
        user.setFailedAttempts(0);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        assertThatThrownBy(() -> authService.login("user@example.com", "wrongPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email or password");

        // failedAttempts was incremented and the user was saved
        assertThat(user.getFailedAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
    }
}
