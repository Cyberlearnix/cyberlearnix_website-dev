package com.cyberlearnix.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        boolean authenticated = false;

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

                String userId = claims.getSubject();
                String role = (String) claims.get("role");

                if (userId != null) {
                    authenticateUser(userId, role, request);
                    authenticated = true;
                }
            } catch (Exception e) {
                // JWT parsing failed — fall through to header-based fallback
                System.err.println("JWT Validation Error: " + e.getMessage());
            }
        }

        // Fallback: use gateway/service-injected headers if JWT was absent or failed to parse
        if (!authenticated) {
            String userId = request.getHeader("X-User-Id");
            String role = request.getHeader("X-User-Role");
            if (userId != null) {
                authenticateUser(userId, role, request);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateUser(String userId, String role, HttpServletRequest request) {
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + (role != null ? role.toUpperCase() : "USER")));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(auth);

        // Set attributes for controller access
        request.setAttribute("X-User-Id", userId);
        if (role != null) {
            request.setAttribute("X-User-Role", role);
        }
    }
}
