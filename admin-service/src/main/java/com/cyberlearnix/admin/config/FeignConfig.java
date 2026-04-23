package com.cyberlearnix.admin.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor serviceAuthInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    // Pass user ID for downstream service identification
                    String userId = auth.getName();
                    if (userId != null) {
                        template.header("X-User-Id", userId);
                    }

                    // Pass role (strip ROLE_ prefix to match gateway convention)
                    String role = auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                            .findFirst()
                            .orElse("ADMIN");
                    template.header("X-User-Role", role);
                }
            }
        };
    }
}
