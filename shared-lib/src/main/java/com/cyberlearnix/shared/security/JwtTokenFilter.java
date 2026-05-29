package com.cyberlearnix.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JwtTokenFilter extends OncePerRequestFilter {

    private final String secret;
    private final java.util.function.Predicate<String> blacklistChecker;

    public JwtTokenFilter(String secret) {
        this.secret = secret;
        this.blacklistChecker = null;
    }

    public JwtTokenFilter(String secret, java.util.function.Predicate<String> blacklistChecker) {
        this.secret = secret;
        this.blacklistChecker = blacklistChecker;
    }

    // Wraps the request to inject extra headers derived from JWT claims
    private static class HeaderInjectingRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> extraHeaders;

        HeaderInjectingRequestWrapper(HttpServletRequest request, Map<String, String> extraHeaders) {
            super(request);
            this.extraHeaders = extraHeaders;
        }

        @Override
        public String getHeader(String name) {
            if (extraHeaders.containsKey(name)) {
                return extraHeaders.get(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (extraHeaders.containsKey(name)) {
                return Collections.enumeration(Collections.singletonList(extraHeaders.get(name)));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.addAll(extraHeaders.keySet());
            return Collections.enumeration(names);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        boolean authenticated = false;
        String resolvedUserId = null;
        String resolvedRole = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // Check blacklist if checker is provided
            if (blacklistChecker != null && blacklistChecker.test(token)) {
                System.err.println("Blacklisted token encountered");
                filterChain.doFilter(request, response);
                return;
            }

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(secret.trim().getBytes()))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                resolvedUserId = claims.getSubject();
                resolvedRole = (String) claims.get("role");

                if (resolvedUserId != null) {
                    authenticated = true;
                }
            } catch (Exception e) {
                // JWT parsing failed — fall through to header-based fallback
                System.err.println("JWT Validation Error [" + e.getClass().getSimpleName() + "]: " + e.getMessage());
            }
        }

        // Fallback: use gateway/service-injected headers if JWT was absent or failed to parse
        if (!authenticated) {
            resolvedUserId = request.getHeader("X-User-Id");
            resolvedRole = request.getHeader("X-User-Role");
        }

        if (resolvedUserId != null) {
            // Inject userId and role as actual HTTP headers so @RequestHeader works in controllers
            Map<String, String> extraHeaders = new HashMap<>();
            extraHeaders.put("X-User-Id", resolvedUserId);
            if (resolvedRole != null) {
                extraHeaders.put("X-User-Role", resolvedRole);
            }
            request = new HeaderInjectingRequestWrapper(request, extraHeaders);
            authenticateUser(resolvedUserId, resolvedRole, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateUser(String userId, String role, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + (role != null ? role.toUpperCase() : "USER")));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(auth);

        // Set attributes for controller access (kept for backward compatibility)
        request.setAttribute("X-User-Id", userId);
        if (role != null) {
            request.setAttribute("X-User-Role", role);
        }
    }
}

