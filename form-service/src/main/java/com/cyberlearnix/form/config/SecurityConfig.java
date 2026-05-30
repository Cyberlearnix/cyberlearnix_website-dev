package com.cyberlearnix.form.config;

import com.cyberlearnix.shared.security.JwtTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Primary
@Order(1)
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(
                                    HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"FORMS_SERVICE_UNAUTHORIZED\", \"message\": \"" + authException.getMessage() + "\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/forms/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Public access for forms
                        .requestMatchers(HttpMethod.GET, "/api/forms/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/forms/*/public/**").permitAll()
                        // Public access for payment callbacks (called by PayU)
                        .requestMatchers("/api/forms/payments/callback/**").permitAll()
                        .requestMatchers("/api/forms/payments/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/forms/payments/price/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/forms/payments/initiate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/forms/payments/status/**").permitAll()
                        // Public access for responses (submission and check)
                        .requestMatchers(HttpMethod.POST, "/api/forms/*/responses").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/forms/*/responses/check").permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtTokenFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
