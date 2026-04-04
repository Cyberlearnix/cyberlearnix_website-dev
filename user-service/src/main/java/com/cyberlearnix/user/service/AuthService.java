package com.cyberlearnix.user.service;

import com.cyberlearnix.shared.entity.PasswordResetToken;
import com.cyberlearnix.shared.entity.RefreshToken;
import com.cyberlearnix.shared.entity.User;
import com.cyberlearnix.shared.entity.UserProfile;
import com.cyberlearnix.shared.repository.PasswordResetTokenRepository;
import com.cyberlearnix.shared.repository.RefreshTokenRepository;
import com.cyberlearnix.shared.repository.UserProfileRepository;
import com.cyberlearnix.shared.repository.UserRepository;
import com.cyberlearnix.shared.entity.BlacklistedToken;
import com.cyberlearnix.shared.repository.BlacklistedTokenRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${jwt.secret:cyberlearnix-secure-and-ultra-long-secret-key-for-jwt-signing-2026-highly-confidential-512-bit-compliant}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}") // 1 hour for access token
    private long jwtExpiration;

    @Value("${jwt.refreshExpiration:7200000}") // 2 hours for refresh token
    private long refreshExpiration;

    private static final int MAX_FAILED_ATTEMPTS = 5;

    public Map<String, Object> register(String email, String password, String role) {
        String sanitizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmail(sanitizedEmail).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(sanitizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setIsFirstLogin(true);
        user = userRepository.save(user);

        // Create initial profile
        UserProfile profile = new UserProfile();
        profile.setId(user.getId());
        profile.setEmail(sanitizedEmail);
        profile.setRole(role);
        profile.setIsActive(true);
        userProfileRepository.save(profile);

        return Map.of("id", user.getId(), "email", user.getEmail(), "role", user.getRole());
    }

    @Transactional
    public Map<String, Object> login(String email, String password) {
        String sanitizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // Check if account is locked
        if (user.getFailedAttempts() != null && user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new RuntimeException(
                    "Account is locked due to too many failed attempts. Please reset your password.");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int attempts = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
            user.setFailedAttempts(attempts);
            userRepository.save(user);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                throw new RuntimeException("Account locked. Too many failed attempts.");
            }
            throw new RuntimeException(
                    "Invalid email or password. Attempts remaining: " + (MAX_FAILED_ATTEMPTS - attempts));
        }

        // Reset failed attempts on success
        user.setFailedAttempts(0);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = generateToken(user);
        String refreshToken = createRefreshToken(user.getId());

        return Map.of(
                "token", accessToken,
                "refreshToken", refreshToken,
                "user", Map.of("id", user.getId(), "email", user.getEmail(), "role", user.getRole(), "isFirstLogin",
                        Boolean.TRUE.equals(user.getIsFirstLogin())));
    }

    @Transactional
    public String createRefreshToken(String userId) {
        refreshTokenRepository.deleteByUserId(userId);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setToken(generateRefreshToken(userId));
        refreshToken.setExpiry(LocalDateTime.now().plusNanos(refreshExpiration * 1000000));
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getToken();
    }

    @Transactional
    public Map<String, Object> refreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (refreshToken.getExpiry().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RuntimeException("Refresh token expired. Please login again.");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccessToken = generateToken(user);
        return Map.of("token", newAccessToken, "refreshToken", token);
    }

    @Transactional
    public void logout(String refreshToken, String accessToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
        if (accessToken != null) {
            BlacklistedToken blacklistedToken = new BlacklistedToken();
            blacklistedToken.setToken(accessToken);
            blacklistedToken.setExpiryDate(LocalDateTime.now().plusNanos(jwtExpiration * 1000000));
            blacklistedTokenRepository.save(blacklistedToken);
        }
    }

    @Transactional
    public String createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new RuntimeException("User not found"));

        passwordResetTokenRepository.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getId());
        resetToken.setToken(token);
        resetToken.setExpiry(LocalDateTime.now().plusHours(1)); // 1 hour expiry
        passwordResetTokenRepository.save(resetToken);

        return token;
    }

    @Transactional
    public void resetPasswordWithToken(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (resetToken.getExpiry().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken);
            throw new RuntimeException("Reset token expired");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setIsFirstLogin(false);
        user.setFailedAttempts(0); // Unlock account on reset
        userRepository.save(user);

        passwordResetTokenRepository.delete(resetToken);
    }

    public Map<String, Object> loginByEmail(String email) {
        String sanitizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = generateToken(user);
        return Map.of(
                "token", token,
                "user", Map.of("id", user.getId(), "email", user.getEmail(), "role", user.getRole(), "isFirstLogin",
                        Boolean.TRUE.equals(user.getIsFirstLogin())));
    }

    public void updatePasswordByEmail(String email, String newPassword) {
        String sanitizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setIsFirstLogin(false);
        userRepository.save(user);
    }

    public void updatePassword(String userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setIsFirstLogin(false);
        userRepository.save(user);
    }

    public Map<String, Object> switchRole(String userId, String targetRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean canSwitch = "admin".equals(user.getRole()) ||
                ("dual".equals(user.getRole()) && ("teacher".equals(targetRole) || "student".equals(targetRole)));

        if (!canSwitch && !user.getRole().equals(targetRole)) {
            throw new RuntimeException("No permission to switch to this role");
        }

        User tempUser = new User();
        tempUser.setId(user.getId());
        tempUser.setRole(targetRole);

        String newToken = generateToken(tempUser);
        return Map.of("token", newToken, "role", targetRole);
    }

    private String generateToken(User user) {
        return Jwts.builder()
                .claim("role", user.getRole())
                .claim("type", "access")
                .subject(user.getId())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();
    }

    private String generateRefreshToken(String userId) {
        return Jwts.builder()
                .claim("type", "refresh")
                .subject(userId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();
    }
}
