package com.cyberlearnix.enrollment;

import com.cyberlearnix.shared.security.JwtTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:5174}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        java.util.List<String> origins = Arrays.asList(corsAllowedOrigins.split(","));
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(request -> {
                    var corsConfig = new org.springframework.web.cors.CorsConfiguration();
                    String path = request.getRequestURI();
                    // PayU posts callbacks & webhook from its own domain — must allow any origin
                    boolean isPayuEndpoint = path.contains("/payments/callback")
                            || path.contains("/payments/webhook");
                    if (isPayuEndpoint) {
                        corsConfig.setAllowedOriginPatterns(java.util.List.of("*"));
                        corsConfig.setAllowCredentials(false);
                    } else {
                        corsConfig.setAllowedOrigins(origins);
                    }
                    corsConfig.setAllowedOrigins(origins);
                    corsConfig.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    corsConfig.setAllowedHeaders(java.util.List.of("*"));
                    return corsConfig;
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Spring Boot error dispatcher — must be public or it returns 403 on any controller exception
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/enrollments/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Public: form config lookup (embed on website without login)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/enrollments/config").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/enrollments/forms/**").permitAll()
                        // Public: student receipt download after PayU redirect (no JWT available)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/enrollments/responses/*/receipt").permitAll()
                        // Public: duplicate-response check before submitting
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/enrollments/responses/check").permitAll()
                        // Public: submit enrollment form response
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/enrollments/responses").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/enrollments/responses/**").permitAll()
                        // Public: payment initiation (student starts payment after form submit)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/enrollments/payments/initiate").permitAll()
                        // Public: PayU callbacks & webhook (PayU posts to these — no JWT)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/enrollments/payments/callback/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/enrollments/payments/webhook").permitAll()
                        // Public: legacy payment endpoint
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/enrollments/payu-payment").permitAll()
                        // Public: payment status check by student
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/enrollments/payments/status/**").permitAll()
                        // Public: enrollment check (inter-service call from course-service)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/enrollments/check").permitAll()
                        // Public: coupon validate (students check discount without auth)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/enrollments/coupons/validate").permitAll()
                        // Public: create a new enrollment submission (application form)
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/enrollments/submissions").permitAll()
                        // Everything else requires auth
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtTokenFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
