package com.cyberlearnix.user.security;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting filter to prevent brute force attacks
 * Limits login attempts per IP address
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${security.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${enquiry.rate-limit.max-attempts:10}")
    private int enquiryMaxAttempts;

    @Value("${security.rate-limit.window-minutes:15}")
    private int windowMinutes;

    private LoadingCache<String, AtomicInteger> cache;

    public RateLimitingFilter() {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .build(new CacheLoader<String, AtomicInteger>() {
                    @Override
                    public AtomicInteger load(String key) {
                        return new AtomicInteger(0);
                    }
                });
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        boolean isLogin = uri.contains("/api/auth/login");
        boolean isEnquiryPost = "POST".equalsIgnoreCase(request.getMethod()) && uri.endsWith("/api/public/enquiries");

        // Apply rate limiting to login and public business enquiry submission endpoints
        if (!isLogin && !isEnquiryPost) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String cacheKey = isEnquiryPost ? "enquiry:" + clientIp : clientIp;
        int limit = isEnquiryPost ? enquiryMaxAttempts : maxAttempts;

        try {
            int attempts = cache.get(cacheKey).incrementAndGet();

            if (attempts > limit) {
                log.warn("Rate limit exceeded for key {}: {} - Attempts: {}", cacheKey, uri, attempts);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                String msg = isEnquiryPost
                        ? "Too many enquiry submissions. Please wait " + windowMinutes + " minutes before trying again."
                        : "Too many login attempts. Please try again in " + windowMinutes + " minutes.";
                response.getWriter().write("{\"error\": \"" + msg + "\", \"message\": \"" + msg + "\", \"timestamp\": " + System.currentTimeMillis() + "}");
                return;
            }

            // Add headers to inform client about rate limit
            response.addHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.addHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - attempts)));
            response.addHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() + (windowMinutes * 60 * 1000L)));

            filterChain.doFilter(request, response);

        } catch (ExecutionException e) {
            log.error("Error in rate limiting: ", e);
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Extract client IP address, considering proxy headers
     */
    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return forwardedFor.split(",")[0].trim();
        }

        String clientIp = request.getHeader("X-Client-IP");
        if (clientIp != null && !clientIp.isEmpty()) {
            return clientIp;
        }

        return request.getRemoteAddr();
    }
}
