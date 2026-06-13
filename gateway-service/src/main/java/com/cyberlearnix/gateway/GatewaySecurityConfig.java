package com.cyberlearnix.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Value("${cors.allowed-origins:https://cyberlearnix.com,http://localhost:3000,http://localhost:5173,http://localhost:5174}")
    private String allowedOrigins;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable()) // Gateway is stateless JWT — CSRF not applicable // NOSONAR java:S4502
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Handle OPTIONS preflights at security layer
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll() // Auth delegated to JwtAuthenticationFilter // NOSONAR java:S4834
            );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Allowed origin patterns — supports wildcards like https://*.netlify.app
        // setAllowedOriginPatterns is required when allowCredentials=true and wildcard patterns are needed
        corsConfig.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));

        // Allowed methods
        corsConfig.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // Allowed headers - specify exact headers when credentials are enabled (CORS security requirement)
        corsConfig.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "x-user-id",
            "x-user-role",
            "Content-Disposition"
        ));

        // Allow credentials — origins are explicitly allowlisted, not wildcard // NOSONAR java:S5122
        corsConfig.setAllowCredentials(true);

        // Exposed headers
        corsConfig.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type"
        ));

        // Max age for preflight requests
        corsConfig.setMaxAge(3600L);

        // Permissive CORS config for third-party payment gateways (PayU)
        CorsConfiguration publicCorsConfig = new CorsConfiguration();
        publicCorsConfig.setAllowedOriginPatterns(Arrays.asList("*"));
        publicCorsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS"));
        publicCorsConfig.setAllowedHeaders(Arrays.asList("*"));
        publicCorsConfig.setAllowCredentials(false);
        publicCorsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/enrollments/payments/callback/**", publicCorsConfig);
        source.registerCorsConfiguration("/api/enrollments/payments/webhook", publicCorsConfig);
        source.registerCorsConfiguration("/api/forms/payments/callback/**", publicCorsConfig);
        source.registerCorsConfiguration("/api/forms/payments/webhook", publicCorsConfig);
        
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE) // Highest precedence: runs before Spring Security + gateway routing
    public CorsWebFilter corsWebFilter() {
        return new CorsWebFilter(corsConfigurationSource());
    }
}
