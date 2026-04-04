package com.cyberlearnix.course;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.cyberlearnix.shared.security.JwtTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/courses/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/courses").permitAll()
                        .requestMatchers("/api/courses/{id}").permitAll()
                        .requestMatchers("/api/courses/*/curriculum").permitAll()
                        .requestMatchers("/api/courses/progress/**").authenticated()
                        .requestMatchers("/api/course-management/**").authenticated()
                        .requestMatchers("/api/materials/upload/**").authenticated()
                        .requestMatchers("/api/content-reviews", "/api/content-reviews/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtTokenFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
