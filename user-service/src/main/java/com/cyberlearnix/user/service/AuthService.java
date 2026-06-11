package com.cyberlearnix.user.service;

import com.cyberlearnix.shared.entity.user.PasswordResetToken;
import com.cyberlearnix.shared.entity.user.RefreshToken;
import com.cyberlearnix.shared.entity.user.User;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.user.PasswordResetTokenRepository;
import com.cyberlearnix.shared.repository.user.RefreshTokenRepository;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import com.cyberlearnix.shared.repository.user.UserRepository;
import com.cyberlearnix.shared.entity.user.BlacklistedToken;
import com.cyberlearnix.shared.repository.user.BlacklistedTokenRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
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

    @Autowired
    private EnrollmentCardService enrollmentCardService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.refreshSecret:${jwt.secret}}")
    private String jwtRefreshSecret;

    @Value("${jwt.expiration:900000}")
    private long jwtExpiration;

    @Value("${jwt.refreshExpiration:2592000000}")
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

        // Issue enrollment card (number + QR code) for students
        if ("student".equalsIgnoreCase(role)) {
            enrollmentCardService.issueCard(profile);
        }

        return Map.of("id", user.getId(), "email", user.getEmail(), "role", user.getRole());
    }

    @Transactional
    public Map<String, Object> login(String email, String password) {
        return login(email, password, null);
    }

    @Transactional
    public Map<String, Object> login(String email, String password, String deviceFingerprint) {
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
        LocalDateTime previousLastLogin = user.getLastLogin(); // capture before overwrite
        user.setFailedAttempts(0);
        user.setLastLogin(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        userRepository.save(user);

        String accessToken = generateToken(user);
        String refreshToken = createRefreshToken(user.getId(), deviceFingerprint, null);

        // Format lastLoginAt with IST offset so frontend interprets timezone correctly
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        String lastLoginAtStr = previousLastLogin != null
                ? previousLastLogin.atZone(ist).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                : null;

        Map<String, Object> userMap = new java.util.HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole());
        userMap.put("isFirstLogin", Boolean.TRUE.equals(user.getIsFirstLogin()));
        userMap.put("lastLoginAt", lastLoginAtStr);

        return Map.of(
                "token", accessToken,
                "refreshToken", refreshToken,
                "user", userMap);
    }

    @Transactional
    public String createRefreshToken(String userId) {
        return createRefreshToken(userId, null, null);
    }

    @Transactional
    public String createRefreshToken(String userId, String deviceFingerprint, String familyId) {
        refreshTokenRepository.deleteByUserId(userId);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setToken(generateRefreshToken(userId));
        refreshToken.setExpiry(LocalDateTime.now().plusNanos(refreshExpiration * 1000000));
        refreshToken.setDeviceFingerprint(deviceFingerprint);
        refreshToken.setFamilyId(familyId != null ? familyId : UUID.randomUUID().toString());
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getToken();
    }

    @Transactional
    public Map<String, Object> refreshToken(String token, String deviceFingerprint) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or revoked refresh token. Please login again."));

        // Detect token reuse — if token is found but already past expiry, it may be a stolen reused token
        if (refreshToken.getExpiry().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RuntimeException("Refresh token expired. Please login again.");
        }

        // Device binding check — reject if fingerprint doesn't match
        if (deviceFingerprint != null && refreshToken.getDeviceFingerprint() != null
                && !deviceFingerprint.equals(refreshToken.getDeviceFingerprint())) {
            // Suspected theft — invalidate entire token family
            refreshTokenRepository.deleteByUserId(refreshToken.getUserId());
            throw new RuntimeException("Security alert: device mismatch detected. Please login again.");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Rotate: delete old, issue new refresh token in same family
        String familyId = refreshToken.getFamilyId();
        refreshTokenRepository.delete(refreshToken);
        String newRefreshToken = createRefreshToken(user.getId(), deviceFingerprint, familyId);

        String newAccessToken = generateToken(user);
        return Map.of("token", newAccessToken, "refreshToken", newRefreshToken);
    }

    @Transactional
    public Map<String, Object> refreshToken(String token) {
        return refreshToken(token, null);
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

    @Transactional
    public Map<String, Object> loginByEmail(String email) {
        String sanitizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime previousLastLogin = user.getLastLogin();
        user.setLastLogin(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        userRepository.save(user);

        String token = generateToken(user);
        String refreshToken = createRefreshToken(user.getId(), null, null);

        ZoneId ist = ZoneId.of("Asia/Kolkata");
        String lastLoginAtStr = previousLastLogin != null
                ? previousLastLogin.atZone(ist).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                : null;

        Map<String, Object> userMap = new java.util.HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole());
        userMap.put("isFirstLogin", Boolean.TRUE.equals(user.getIsFirstLogin()));
        userMap.put("lastLoginAt", lastLoginAtStr);

        return Map.of(
                "token", token,
                "refreshToken", refreshToken,
                "user", userMap);
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

    /**
     * First-login password setup — called by authenticated user whose isFirstLogin=true.
     * Sets the new password, clears isFirstLogin flag.
     * Does NOT require old password (admin-assigned temp password flow).
     */
    public void setFirstLoginPassword(String userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!Boolean.TRUE.equals(user.getIsFirstLogin())) {
            throw new RuntimeException("First-login password setup already completed. Use change-password instead.");
        }
        if (newPassword.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters");
        }
        if (!newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
            throw new RuntimeException("Password must contain uppercase, lowercase, number, and special character");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setIsFirstLogin(false);
        userRepository.save(user);
    }

    public Map<String, Object> switchRole(String userId, String targetRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Role → allowed target roles for view-switching
        java.util.Map<String, java.util.List<String>> allowedSwitches = java.util.Map.of(
            "admin",     java.util.List.of("teacher", "student", "institute"),
            "institute", java.util.List.of("teacher", "student"),
            "dual",      java.util.List.of("teacher", "student"),
            "teacher",   java.util.List.of("student")
        );

        java.util.List<String> allowed = allowedSwitches.getOrDefault(user.getRole(), java.util.List.of());

        if (!allowed.contains(targetRole)) {
            throw new RuntimeException("No permission to switch to role: " + targetRole);
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
                .signWith(Keys.hmacShaKeyFor(jwtRefreshSecret.getBytes()))
                .compact();
    }

    /**
     * Generates a SHA-256 fingerprint from userAgent + IP for device binding.
     */
    public static String deviceFingerprint(String userAgent, String remoteIp) {
        try {
            String raw = (userAgent != null ? userAgent : "") + "|" + (remoteIp != null ? remoteIp : "");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
