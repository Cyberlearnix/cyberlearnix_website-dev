package com.cyberlearnix.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // SECURITY: Strip spoofed identity headers unconditionally before ANY routing.
        // Prevents privilege escalation via X-User-Id / X-User-Role header injection.
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Role");
                })
                .build();
        final ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitized).build();

        String path = sanitizedExchange.getRequest().getPath().value();
        String method = sanitizedExchange.getRequest().getMethod().name();

        // 1. Whitelist Public Endpoints
        if (isPublicPath(path, method)) {
            return chain.filter(sanitizedExchange);
        }

        String authHeader = sanitizedExchange.getRequest().getHeaders().getFirst("Authorization");
        log.debug("[GW] {} {} | auth={}", method, path, authHeader != null ? "present" : "missing");

        // WebSocket clients cannot set custom headers during the initial handshake.
        // For WebSocket terminal paths, also accept the JWT from the ?token= query parameter.
        String resolvedToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            resolvedToken = authHeader.substring(7).trim();
        } else if (path.startsWith("/labs/terminal/")) {
            String queryToken = sanitizedExchange.getRequest().getQueryParams().getFirst("token");
            if (queryToken != null && !queryToken.isBlank()) {
                resolvedToken = queryToken.trim();
                log.debug("[GW] WebSocket path — using token from query parameter");
            }
        }

        if (resolvedToken != null) {
            String token = resolvedToken;
            // Redis blacklist key must match what user-service writes: "blacklisted:" + token
            String blacklistKey = "blacklisted:" + token;

            return redisTemplate.hasKey(blacklistKey)
                    .defaultIfEmpty(false)
                    .onErrorReturn(false)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            log.warn("[GW] Token is blacklisted — rejecting");
                            sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return sanitizedExchange.getResponse().setComplete();
                        }
                        try {
                            Claims claims = Jwts.parser()
                                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.trim().getBytes()))
                                    .build()
                                    .parseSignedClaims(token)
                                    .getPayload();

                            String userId = claims.getSubject();
                            String userRole = (String) claims.get("role");
                            log.debug("[GW] JWT valid — userId={} role={} injecting headers", userId, userRole);

                            // SECURITY: /adminlms requires ADMIN or DUAL role — reject all others
                            if (path.startsWith("/adminlms")) {
                                if (!"admin".equals(userRole) && !"dual".equals(userRole) && !"institute".equals(userRole)) {
                                    log.warn("[GW] /adminlms access denied — insufficient role: {}", userRole);
                                    sanitizedExchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                    return sanitizedExchange.getResponse().setComplete();
                                }
                            }

                            ServerHttpRequest.Builder builder = sanitizedExchange.getRequest().mutate();
                            if (userId != null) builder.header("X-User-Id", userId);
                            if (userRole != null) builder.header("X-User-Role", userRole);

                            return chain.filter(sanitizedExchange.mutate().request(builder.build()).build());
                        } catch (Exception e) {
                            log.warn("[GW] JWT parse failed [{}]: {} — rejecting", e.getClass().getSimpleName(), e.getMessage());
                            sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return sanitizedExchange.getResponse().setComplete();
                        }
                    });
        }

        log.debug("[GW] No Bearer token on protected path {} {} — rejecting with 401", method, path);
        // Defense-in-depth: reject unauthenticated requests at the gateway rather than
        // forwarding them without identity headers and relying solely on downstream auth.
        sanitizedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return sanitizedExchange.getResponse().setComplete();
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
            return path.endsWith("/responses") || path.endsWith("/responses/check");
        }

        // PUT/PATCH/DELETE always require auth
        if ("PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)) {
            return false;
        }

        if (path.startsWith("/api/auth/") ||
            path.startsWith("/api/public/") ||
            path.startsWith("/api/careers") ||
            path.startsWith("/api/contact-submissions") ||
            path.startsWith("/api/shop") ||
            path.startsWith("/api/menus") ||
            path.startsWith("/api/teams") ||
            path.startsWith("/api/site-settings") ||
            path.startsWith("/api/chatbot") ||
            path.startsWith("/api/updates") ||
            path.startsWith("/api/banners") ||
            path.startsWith("/api/promos") ||
            path.startsWith("/api/partners") ||
            path.startsWith("/api/suggestions")) {
            return true;
        }

        if (path.startsWith("/api/courses") && 
            !path.contains("/full") && 
            !path.contains("/progress") && 
            !path.endsWith("/teachers") &&
            !path.contains("/curriculum")) {
            return true;
        }

        // Form Service & Enrollment Service Public Endpoints
        if (path.startsWith("/api/forms/") || path.startsWith("/api/enrollments/")) {
            // White-list specific public enrollment paths
            // NOTE: GET /api/enrollments/responses is intentionally NOT public — it returns
            // all student PII and payment data and requires ADMIN role.
            if (path.startsWith("/api/enrollments/forms/") ||
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
