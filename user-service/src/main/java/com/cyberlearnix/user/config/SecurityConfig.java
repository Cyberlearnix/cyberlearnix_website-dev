package com.cyberlearnix.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import com.cyberlearnix.shared.security.JwtTokenFilter;
import com.cyberlearnix.user.security.RateLimitingFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.cyberlearnix.shared.repository.user.BlacklistedTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

        @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:5174}")
    private String allowedOrigins;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disabled — JWT Bearer token auth is stateless (no session cookies)
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                
                // Return 401 for unauthenticated requests (not 403)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                
                // Security Headers
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(buildContentSecurityPolicy())
                        )
                        .xssProtection(xss -> xss.disable())
                        .frameOptions(frame -> frame.deny())
                        .permissionsPolicy(permissions -> permissions
                                .policy("geolocation=(), microphone=(), camera=()")
                        )
                )
                
                // CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Session Management
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                
                // Authorization Rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/request-otp", "/api/auth/verify-otp", "/api/auth/refresh-token").permitAll()
                        .requestMatchers("/api/auth/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/careers", "/api/careers/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/contact-submissions").permitAll()
                        .requestMatchers("/api/contact-submissions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/chatbot", "/api/chatbot/**").permitAll()
                        .requestMatchers("/api/chatbot", "/api/chatbot/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/menus", "/api/menus/**").permitAll()
                        .requestMatchers("/api/menus", "/api/menus/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/teams", "/api/teams/**").permitAll()
                        .requestMatchers("/api/teams", "/api/teams/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/site-settings", "/api/site-settings/**").permitAll()
                        .requestMatchers("/api/site-settings", "/api/site-settings/**").hasRole("ADMIN")
                        .requestMatchers("/api/activity/logs", "/api/activity/logs/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/stats/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/profile").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/profile").authenticated()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/auth/logout").authenticated()
                        .anyRequest().authenticated()
                )
                
                // Filters
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtTokenFilter(jwtSecret, token -> blacklistedTokenRepository.findByToken(token).isPresent()), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        // Parse allowed origins from properties
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                "X-Total-Count",
                "X-Page-Number"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

        private String buildContentSecurityPolicy() {
                String connectSrc = Arrays.stream(allowedOrigins.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .reduce("connect-src 'self'", (policy, origin) -> policy + " " + origin);

                return "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; "
                                + connectSrc;
        }
}
