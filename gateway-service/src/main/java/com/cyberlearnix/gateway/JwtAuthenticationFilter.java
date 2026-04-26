package com.cyberlearnix.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        // 1. Whitelist Public Endpoints
        if (isPublicPath(path, method)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        System.err.println("[GW] " + method + " " + path + " | auth=" + (authHeader != null ? "present" : "missing"));

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            // Redis blacklist key must match what user-service writes: "blacklisted:" + token
            String blacklistKey = "blacklisted:" + token;

            return redisTemplate.hasKey(blacklistKey)
                    .defaultIfEmpty(false)
                    .onErrorReturn(false)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            System.err.println("[GW] Token is blacklisted — rejecting");
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                        try {
                            Claims claims = Jwts.parser()
                                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.trim().getBytes()))
                                    .build()
                                    .parseSignedClaims(token)
                                    .getPayload();

                            String userId = claims.getSubject();
                            String userRole = (String) claims.get("role");
                            System.err.println("[GW] JWT valid — userId=" + userId + " role=" + userRole + " injecting headers");

                            ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
                            if (userId != null) builder.header("X-User-Id", userId);
                            if (userRole != null) builder.header("X-User-Role", userRole);

                            return chain.filter(exchange.mutate().request(builder.build()).build());
                        } catch (Exception e) {
                            System.err.println("[GW] JWT parse failed [" + e.getClass().getSimpleName() + "]: " + e.getMessage() + " — rejecting");
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }
                    });
        }

        System.err.println("[GW] No Bearer token — forwarding without auth headers");
        // No token — let downstream service enforce its own security
        return chain.filter(exchange);
    }

    private boolean isPublicPath(String path, String method) {
        // Public POST endpoints (no auth required)
        if ("POST".equals(method)) {
            if (path.startsWith("/api/auth/")) return true;
            if (path.equals("/api/enrollments/responses")) return true;
            if (path.startsWith("/api/enrollments/payments/callback/")) return true;
            if (path.equals("/api/enrollments/payments/initiate")) return true;
            if (path.equals("/api/enrollments/payments/webhook")) return true;
            if (path.equals("/api/enrollments/payu-payment")) return true;
            if (path.endsWith("/responses") || path.endsWith("/responses/check")) return true;
            return false;
        }

        // PUT/PATCH/DELETE always require auth
        if ("PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)) {
            return false;
        }

        if (path.startsWith("/api/auth/") || 
            path.startsWith("/api/careers") ||
            path.startsWith("/api/contact-submissions") || 
            path.startsWith("/api/shop") ||
            path.equals("/swagger-ui.html") ||
            path.startsWith("/swagger-ui/") ||
            path.startsWith("/v3/api-docs") ||
            path.contains("/v3/api-docs")) {
            return true;
        }

        if (path.startsWith("/api/courses") && 
            !path.contains("/full") && 
            !path.contains("/progress") && 
            !path.endsWith("/teachers")) {
            return true;
        }

        // Form Service & Enrollment Service Public Endpoints
        if (path.startsWith("/api/forms/") || path.startsWith("/api/enrollments/")) {
            // WHite-list specific public enrollment paths
            if (path.startsWith("/api/enrollments/forms/") || 
                path.equals("/api/enrollments/responses") || 
                path.equals("/api/enrollments/responses/check")) {
                return true;
            }

            // Form Service logic
            if (path.startsWith("/api/forms/")) {
                String remaining = path.substring("/api/forms/".length());
                if (!remaining.isEmpty() && !remaining.contains("/")) {
                    return true; // Matches /api/forms/{id}
                }
                if (path.endsWith("/responses") || path.endsWith("/responses/check")) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
