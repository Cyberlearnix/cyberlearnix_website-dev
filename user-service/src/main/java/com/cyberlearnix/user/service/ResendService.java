package com.cyberlearnix.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Map;
import java.util.List;

@Service
public class ResendService {

    @Value("${resend.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean sendEmail(String to, String subject, String htmlContent) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("${")) {
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "from", "Cyberlearnix <academy@cyberlearnix.com>",
                    "to", List.of(to),
                    "subject", subject,
                    "html", htmlContent);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity("https://api.resend.com/emails", entity,
                    String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            System.err.println("Resend email failed: " + e.getMessage());
            return false;
        }
    }
}
