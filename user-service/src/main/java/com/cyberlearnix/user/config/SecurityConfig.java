package com.cyberlearnix.user.config;

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
import com.cyberlearnix.shared.repository.BlacklistedTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/auth/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/users").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/careers", "/api/careers/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact-submissions").permitAll()
                        .requestMatchers("/api/contact-submissions/**").hasRole("ADMIN")
                        
                        // Explicitly secure destructive endpoints
                        .requestMatchers(HttpMethod.GET, "/api/chatbot", "/api/chatbot/**").permitAll()
                        .requestMatchers("/api/chatbot", "/api/chatbot/**").hasRole("ADMIN")
                        
                        .requestMatchers(HttpMethod.GET, "/api/menus", "/api/menus/**").permitAll()
                        .requestMatchers("/api/menus", "/api/menus/**").hasRole("ADMIN")
                        
                        .requestMatchers(HttpMethod.GET, "/api/teams", "/api/teams/**").permitAll()
                        .requestMatchers("/api/teams", "/api/teams/**").hasRole("ADMIN")
                        
                        .requestMatchers(HttpMethod.GET, "/api/site-settings", "/api/site-settings/**").permitAll()
                        .requestMatchers("/api/site-settings", "/api/site-settings/**").hasRole("ADMIN")
                        
                        .requestMatchers("/api/activity/logs", "/api/activity/logs/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/profile").authenticated()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtTokenFilter(jwtSecret, token -> blacklistedTokenRepository.findByToken(token).isPresent()), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
