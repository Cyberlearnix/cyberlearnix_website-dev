package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.repository.user.UserRepository;
import com.cyberlearnix.user.dto.*;
import com.cyberlearnix.user.service.AuthService;
import com.cyberlearnix.user.service.OtpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private OtpService otpService;

    @Autowired
    private UserRepository userRepository;



    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            String email = loginRequest.getEmail();
            String password = loginRequest.getPassword();

            if (email == null || password == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email and password are required"));
            }

            Map<String, Object> result = authService.login(email, password);
            return ResponseEntity.ok(result);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
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
                        .body(Map.of("error", "Only administrators can register students and teachers"));
            }

            String email = registerRequest.getEmail();
            String password = registerRequest.getPassword();
            String role = registerRequest.getRole() != null ? registerRequest.getRole() : "student";

            if (email == null || password == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email and password are required"));
            }

            // Enforce minimum password strength
            if (password.length() < 8) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Password must be at least 8 characters"));
            }

            Map<String, Object> result = authService.register(email, password, role);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // Request OTP for password reset
    @PostMapping("/request-otp")
    public ResponseEntity<?> requestOtp(@RequestBody OtpRequest otpRequest) {
        String email = otpRequest.getEmail();
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        email = email.trim().toLowerCase();
        if (otpService.isOtpRateLimited(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many OTP requests. Please wait 15 minutes before trying again."));
        }
        try {
            // Ensure user exists before sending OTP
            authService.createPasswordResetToken(email); // This checks user existence but we don't need the token yet
            String sessionId = otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(Map.of("success", true, "message", "OTP sent to email", "sessionId", sessionId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // Request OTP for Login
    @PostMapping("/request-login-otp")
    public ResponseEntity<?> requestLoginOtp(@RequestBody OtpRequest otpRequest) {
        String email = otpRequest.getEmail();
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        email = email.trim().toLowerCase();

        if (otpService.isOtpRateLimited(email)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many OTP requests. Please wait 15 minutes before trying again."));
        }

        // âœ… CHECK: Email must exist in users table BEFORE sending OTP
        if (userRepository.findByEmail(email).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No account found with this email address."));
        }

        try {
            // Only now generate and send OTP
            String sessionId = otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(Map.of("message", "OTP sent", "sessionId", sessionId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
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
                    .body(Map.of("error", "email, otp, and sessionId are required"));
        }
        email = email.trim().toLowerCase();
        try {
            boolean valid = otpService.verifyOtp(email, otp, sessionId);
            if (!valid) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired OTP"));
            }
            // Generate a reset token that can be used for the actual reset
            String resetToken = authService.createPasswordResetToken(email);
            return ResponseEntity.ok(Map.of("success", true, "resetToken", resetToken));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // Verify OTP for direct login (Passwordless)
    @PostMapping("/verify-otp-login")
    public ResponseEntity<?> verifyOtpLogin(@RequestBody OtpLoginRequest otpLoginRequest) {
        String email = otpLoginRequest.getEmail();
        String otp = otpLoginRequest.getOtp();
        String sessionId = otpLoginRequest.getSessionId();
        if (email == null || email.trim().isEmpty() || otp == null || sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email, OTP and sessionId are required"));
        }
        email = email.trim().toLowerCase();
        try {
            boolean valid = otpService.verifyOtp(email, otp, sessionId);
            if (!valid) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid or expired OTP"));
            }
            // Generate session
            Map<String, Object> session = authService.loginByEmail(email);
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    // Switch role for authorized users (Admin, Dual)
    @PostMapping("/switch-role")
    public ResponseEntity<?> switchRole(@RequestBody RoleSwitchRequest roleSwitchRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = auth.getPrincipal().toString();

        String targetRole = roleSwitchRequest.getRole();
        if (targetRole == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Target role is required"));
        }
        try {
            Map<String, Object> result = authService.switchRole(userId, targetRole);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String accessToken = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
            authService.logout(request.getRefreshToken(), accessToken);
            return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            Map<String, Object> result = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody TokenPasswordResetRequest request) {
        if (request.getNewPassword() == null || !request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
        }
        try {
            authService.resetPasswordWithToken(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("success", true, "message", "Password has been reset successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody PasswordResetRequest request) {
        if (request.getNewPassword() == null || !request.getNewPassword().equals(request.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String userId = auth.getPrincipal().toString();
        try {
            authService.updatePassword(userId, request.getNewPassword());
            return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}
