package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.annotation.AuditLog;
import com.cyberlearnix.shared.repository.user.UserRepository;
import com.cyberlearnix.user.dto.*;
import com.cyberlearnix.user.service.AuthService;
import com.cyberlearnix.user.service.OtpService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String KEY_ERROR = "error";

    @Autowired
    private AuthService authService;

    @Autowired
    private OtpService otpService;

    @Autowired
    private UserRepository userRepository;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpiration;

    /**
     * Secure login endpoint with httpOnly cookie for refresh token
     * Returns access token in body and refresh token in httpOnly cookie
     */
    @PostMapping("/login")
    @AuditLog(action = "LOGIN")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        try {
            String email = loginRequest.getEmail();
            String password = loginRequest.getPassword();

            if (email == null || password == null || email.isEmpty() || password.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(KEY_ERROR, "Email and password are required"));
            }

            // Perform authentication
            Map<String, Object> authResult = authService.login(email, password);
            String accessToken = (String) authResult.get("token");
            String refreshToken = (String) authResult.get("refreshToken");
            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = (Map<String, Object>) authResult.get("user");

            // Set refresh token in httpOnly, Secure, SameSite cookie
            ResponseCookie refreshTokenCookie = ResponseCookie
                    .from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(false) // TODO: Switch to true for HTTPS production
                    .path("/api/auth")
                    .maxAge(7200) // 2 hours
                    .sameSite("Strict")
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

            // Build secure response
            AuthResponse authResponse = AuthResponse.builder()
                    .token(accessToken)
                    .expiresIn(jwtExpiration / 1000) // Convert to seconds
                    .tokenType("Bearer")
                    .user(AuthResponse.UserInfo.builder()
                            .id((String) userMap.get("id"))
                            .email((String) userMap.get("email"))
                            .role((String) userMap.get("role"))
                            .isFirstLogin((Boolean) userMap.getOrDefault("isFirstLogin", false))
                            .lastLoginAt((String) userMap.get("lastLoginAt"))
                            .build())
                    .build();

            log.info("User logged in successfully: {}", email);
            return ResponseEntity.ok(authResponse);

        } catch (RuntimeException e) {
            log.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    /**
     * Secure logout endpoint - invalidates both access and refresh tokens
     */
    @PostMapping("/logout")
    @AuditLog(action = "LOGOUT")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                    @CookieValue(value = "refreshToken", required = false) String refreshToken,
                                    HttpServletResponse response) {
        try {
            String accessToken = (authHeader != null && authHeader.startsWith("Bearer ")) 
                    ? authHeader.substring(7) 
                    : null;

            if (accessToken == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of(KEY_ERROR, "Authorization header required"));
            }

            authService.logout(refreshToken, accessToken);

            // Clear the refresh token cookie
            ResponseCookie clearCookie = ResponseCookie
                    .from("refreshToken", "")
                    .httpOnly(true)
                    .secure(false) // TODO: Switch to true for HTTPS production
                    .path("/api/auth")
                    .maxAge(0) // Delete the cookie
                    .sameSite("Strict")
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());

            log.info("User logged out successfully");
            return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
        } catch (Exception e) {
            log.error("Logout failed: ", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    /**
     * Refresh access token using httpOnly refresh token from cookie
     */
    @PostMapping("/refresh-token")
    @AuditLog(action = "TOKEN_REFRESH")
    public ResponseEntity<?> refreshToken(@CookieValue(value = "refreshToken", required = false) String refreshToken,
                                          HttpServletResponse response) {
        try {
            if (refreshToken == null || refreshToken.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(KEY_ERROR, "Refresh token not found"));
            }

            Map<String, Object> result = authService.refreshToken(refreshToken);
            String newAccessToken = (String) result.get("token");

            AuthResponse authResponse = AuthResponse.builder()
                    .token(newAccessToken)
                    .expiresIn(jwtExpiration / 1000)
                    .tokenType("Bearer")
                    .build();

            log.info("Token refreshed successfully");
            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    /**
     * Verify OTP for login (passwordless)
     */
    @PostMapping("/verify-otp-login")
    @AuditLog(action = "OTP_LOGIN")
    public ResponseEntity<?> verifyOtpLogin(@RequestBody OtpLoginRequest otpLoginRequest,
                                            HttpServletResponse response) {
        String email = otpLoginRequest.getEmail();
        String otp = otpLoginRequest.getOtp();
        String sessionId = otpLoginRequest.getSessionId();

        if (email == null || email.trim().isEmpty() || otp == null || sessionId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "Email, OTP and sessionId are required"));
        }

        email = email.trim().toLowerCase();
        try {
            boolean valid = otpService.verifyOtp(email, otp, sessionId);
            if (!valid) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(KEY_ERROR, "Invalid or expired OTP"));
            }

            Map<String, Object> authResult = authService.loginByEmail(email);
            String accessToken = (String) authResult.get("token");
            String refreshToken = (String) authResult.get("refreshToken");
            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = (Map<String, Object>) authResult.get("user");

            // Set refresh token cookie
            ResponseCookie refreshTokenCookie = ResponseCookie
                    .from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(false) // TODO: Switch to true for HTTPS production
                    .path("/api/auth")
                    .maxAge(7200)
                    .sameSite("Strict")
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

            AuthResponse authResponse = AuthResponse.builder()
                    .token(accessToken)
                    .expiresIn(jwtExpiration / 1000)
                    .tokenType("Bearer")
                    .user(AuthResponse.UserInfo.builder()
                            .id((String) userMap.get("id"))
                            .email((String) userMap.get("email"))
                            .role((String) userMap.get("role"))
                            .isFirstLogin((Boolean) userMap.getOrDefault("isFirstLogin", false))
                            .build())
                    .build();

            log.info("User logged in via OTP: {}", email);
            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            log.warn("OTP login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        try {
            // Validate role from SecurityContext (set by JwtTokenFilter)
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = (auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

            if (!isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(KEY_ERROR, "Only administrators can register students, teachers, and institutes"));
            }

            String email = registerRequest.getEmail();
            String password = registerRequest.getPassword();
            String role = registerRequest.getRole() != null ? registerRequest.getRole() : "student";

            if (email == null || password == null || email.isEmpty() || password.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(KEY_ERROR, "Email and password are required"));
            }

            // Enforce minimum password strength
            if (password.length() < 8) {
                return ResponseEntity.badRequest()
                        .body(Map.of(KEY_ERROR, "Password must be at least 8 characters"));
            }

            if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
                return ResponseEntity.badRequest()
                        .body(Map.of(KEY_ERROR, "Password must contain uppercase, lowercase, number, and special character"));
            }

            Map<String, Object> result = authService.register(email, password, role);
            log.info("New user registered: {} with role: {}", email, role);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (RuntimeException e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    // Request OTP for password reset
    @PostMapping("/request-otp")
    public ResponseEntity<?> requestOtp(@RequestBody OtpRequest otpRequest) {
        String email = otpRequest.getEmail();
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Email is required"));
        }
        email = email.trim().toLowerCase();
        if (otpService.isOtpRateLimited(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(KEY_ERROR, "Too many OTP requests. Please wait 15 minutes before trying again."));
        }
        try {
            // Ensure user exists before sending OTP
            authService.createPasswordResetToken(email);
            String sessionId = otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(Map.of("success", true, "message", "OTP sent to email", "sessionId", sessionId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    // Request OTP for Login
    @PostMapping("/request-login-otp")
    public ResponseEntity<?> requestLoginOtp(@RequestBody OtpRequest otpRequest) {
        String email = otpRequest.getEmail();
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Email is required"));
        }
        email = email.trim().toLowerCase();

        if (otpService.isOtpRateLimited(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(KEY_ERROR, "Too many OTP requests. Please wait 15 minutes before trying again."));
        }

        // Check if user exists
        if (userRepository.findByEmail(email).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_ERROR, "No account found with this email address."));
        }

        try {
            String sessionId = otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(Map.of("message", "OTP sent", "sessionId", sessionId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    // Verify OTP and return reset token
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpLoginRequest verifyRequest) {
        String email = verifyRequest.getEmail();
        String otp = verifyRequest.getOtp();
        String sessionId = verifyRequest.getSessionId();
        if (email == null || email.trim().isEmpty() || otp == null || sessionId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "email, otp, and sessionId are required"));
        }
        email = email.trim().toLowerCase();
        try {
            boolean valid = otpService.verifyOtp(email, otp, sessionId);
            if (!valid) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_ERROR, "Invalid or expired OTP"));
            }
            String resetToken = authService.createPasswordResetToken(email);
            return ResponseEntity.ok(Map.of("success", true, "resetToken", resetToken));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody TokenPasswordResetRequest request) {
        if (request.getNewPassword() == null || !request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Passwords do not match"));
        }
        try {
            authService.resetPasswordWithToken(request.getToken(), request.getNewPassword());
            log.info("Password reset successfully");
            return ResponseEntity.ok(Map.of("success", true, "message", "Password has been reset successfully"));
        } catch (Exception e) {
            log.warn("Password reset failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody PasswordResetRequest request) {
        if (request.getNewPassword() == null || !request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Passwords do not match"));
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = auth.getPrincipal().toString();
        try {
            authService.updatePassword(userId, request.getNewPassword());
            log.info("Password changed successfully for user: {}", userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully"));
        } catch (Exception e) {
            log.warn("Password change failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    @PostMapping("/switch-role")
    public ResponseEntity<?> switchRole(@RequestBody RoleSwitchRequest roleSwitchRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = auth.getPrincipal().toString();

        String targetRole = roleSwitchRequest.getRole();
        if (targetRole == null) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Target role is required"));
        }
        try {
            Map<String, Object> result = authService.switchRole(userId, targetRole);
            log.info("Role switched to: {} for user: {}", targetRole, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Role switch failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_ERROR, e.getMessage()));
        }
    }

    // Request OTP for password reset (DUPLICATE - REMOVED)
    
    // Request OTP for Login (DUPLICATE - REMOVED)

    // Verify OTP and return reset token (DUPLICATE - REMOVED)

    // Verify OTP for direct login (Passwordless) (DUPLICATE - REMOVED)

    // Switch role for authorized users (Admin, Dual) (DUPLICATE - REMOVED)
}
