package com.cyberlearnix.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public class SecurityUtils {
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    public static Optional<String> getUserId(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(USER_ID_HEADER));
    }

    public static Optional<String> getUserRole(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(USER_ROLE_HEADER));
    }

    public static boolean isAdmin(HttpServletRequest request) {
        return getUserRole(request).map("admin"::equals).orElse(false);
    }

    public static boolean isTeacher(HttpServletRequest request) {
        return getUserRole(request).map(role -> "teacher".equals(role) || "dual".equals(role)).orElse(false);
    }

    public static boolean isInstitute(HttpServletRequest request) {
        return getUserRole(request).map("institute"::equals).orElse(false);
    }
}
