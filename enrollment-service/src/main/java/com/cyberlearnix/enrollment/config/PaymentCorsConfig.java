package com.cyberlearnix.enrollment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Arrays;
import java.util.List;

/**
 * CORS Configuration for Payment Callback endpoints.
 * 
 * These endpoints receive POST redirects from PayU payment gateway,
 * so they need to allow cross-origin requests from any origin.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentCorsConfig {

    @Bean
    public CorsFilter paymentCorsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        
        // Configure CORS for payment callback endpoints
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(Arrays.asList("*")); // Allow all origins for callbacks
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS"));
        config.setMaxAge(3600L);
        
        // Apply to payment callback endpoints
        source.registerCorsConfiguration("/api/enrollments/payments/callback/**", config);
        source.registerCorsConfiguration("/api/enrollments/payments/webhook", config);
        
        return new CorsFilter(source);
    }
}
