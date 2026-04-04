package com.cyberlearnix.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.core.Ordered;
import reactor.core.publisher.Mono;
import com.cyberlearnix.shared.repository.BlacklistedTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.scheduler.Schedulers;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 1. Whitelist Public Endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                // Diagnostic log (redacted)
                System.out.println("Gateway validating token for: " + path);
                
                // Blacklist Check
                return Mono.fromCallable(() -> blacklistedTokenRepository.findByToken(token).isPresent())
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(isBlacklisted -> {
                        if (isBlacklisted) {
                            System.err.println("Gateway: Blocking blacklisted token for path: " + path);
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

                            System.out.println("Gateway validated user: " + userId + " with role: " + userRole);

                            ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
                            if (userId != null) builder.header("X-User-Id", userId);
                            if (userRole != null) builder.header("X-User-Role", userRole);

                            return chain.filter(exchange.mutate().request(builder.build()).build());
                        } catch (Exception e) {
                            System.err.println("Gateway JWT Validation Error: " + e.getMessage());
                            return chain.filter(exchange);
                        }
                    });
            } catch (Exception e) {
                // log error but CONTINUE - let the microservice handle security
                System.err.println("Gateway JWT Validation Error for " + path + ": " + e.getMessage());
                // e.printStackTrace(); // Optional for deep debug
            }
        }

        // Proceed without blocking - protected routes will be caught by microservice SecurityConfig
        return chain.filter(exchange);
    }

    private boolean isPublicPath(String path) {
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
