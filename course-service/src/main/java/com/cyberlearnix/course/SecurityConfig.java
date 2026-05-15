package com.cyberlearnix.course;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.cyberlearnix.shared.security.JwtTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"Unauthorized\",\"message\":\"Missing or invalid authentication token\"}");
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"error\":\"Forbidden\",\"message\":\"You do not have permission to access this resource\"}");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/courses/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/banners", "/api/banners/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/promos", "/api/promos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/partners", "/api/partners/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/updates", "/api/updates/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/suggestions", "/api/suggestions/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/certificates", "/api/certificates/**").permitAll()
                        .requestMatchers("/api/courses/*/price").permitAll()
                        .requestMatchers("/api/courses/progress/**").authenticated()
                        .requestMatchers("/api/course-management/**").authenticated()
                        .requestMatchers("/api/materials/upload/**").authenticated()
                        .requestMatchers("/api/materials/drive/upload/**").authenticated()
                        .requestMatchers("/api/content-reviews", "/api/content-reviews/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtTokenFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
