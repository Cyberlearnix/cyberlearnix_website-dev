package com.cyberlearnix.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class TurnstileVerificationService {

    private static final String SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Value("${turnstile.secret-key:}")
    private String secretKey;

    @Value("${turnstile.verify.enabled:true}")
    private boolean verifyEnabled;

    private final RestTemplate restTemplate;

    public TurnstileVerificationService() {
        this.restTemplate = new RestTemplate();
    }

    public TurnstileVerificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean verify(String captchaToken, String remoteIp) {
        if (!verifyEnabled) {
            return true;
        }
        if (secretKey == null || secretKey.isBlank() || secretKey.startsWith("${")) {
            log.warn("Turnstile secret key not configured; skipping external Turnstile verification");
            return true;
        }
        if (captchaToken == null || captchaToken.isBlank()) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", secretKey.trim());
            body.add("response", captchaToken.trim());
            if (remoteIp != null && !remoteIp.isBlank()) {
                body.add("remoteip", remoteIp);
            }

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(SITEVERIFY_URL, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Turnstile verification HTTP failure: status={}", response.getStatusCode());
                return false;
            }
            Object success = response.getBody().get("success");
            boolean ok = Boolean.TRUE.equals(success);
            if (!ok) {
                log.warn("Turnstile verification rejected: {}", response.getBody().get("error-codes"));
            }
            return ok;
        } catch (Exception e) {
            log.error("Turnstile verification request failed: {}", e.getMessage());
            return false;
        }
    }
}
