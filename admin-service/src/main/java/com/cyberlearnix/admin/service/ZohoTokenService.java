package com.cyberlearnix.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Manages the Zoho OAuth 2.0 access token lifecycle.
 *
 * Zoho access tokens expire after 1 hour. This service caches the token
 * and refreshes it automatically when it is within 60 seconds of expiry.
 *
 * One-time setup:
 *  1. https://api-console.zoho.com -> Add Client -> Server-based Applications
 *  2. Generate a Self-Client code with scope: ZohoMeeting.meeting.ALL
 *  3. Exchange via:
 *     POST https://accounts.zoho.com/oauth/v2/token
 *       ?code=<code>&client_id=<id>&client_secret=<secret>&redirect_uri=<uri>&grant_type=authorization_code
 *  4. Save the returned refresh_token in your environment as ZOHO_REFRESH_TOKEN
 */
@Service
public class ZohoTokenService {

    @Value("${zoho.client-id}")
    private String clientId;

    @Value("${zoho.client-secret}")
    private String clientSecret;

    @Value("${zoho.refresh-token}")
    private String refreshToken;

    @Value("${zoho.accounts-url:https://accounts.zoho.com}")
    private String accountsUrl;

    private final RestTemplate restTemplate;

    private String cachedAccessToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public ZohoTokenService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Returns a valid access token, refreshing if necessary.
     */
    public synchronized String getAccessToken() {
        if (cachedAccessToken == null || Instant.now().isAfter(tokenExpiry.minusSeconds(60))) {
            doRefresh();
        }
        return cachedAccessToken;
    }

    @SuppressWarnings("unchecked")
    private void doRefresh() {
        String url = accountsUrl
                + "/oauth/v2/token"
                + "?refresh_token={refreshToken}"
                + "&client_id={clientId}"
                + "&client_secret={clientSecret}"
                + "&grant_type=refresh_token";

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url, null, Map.class,
                Map.of("refreshToken", refreshToken,
                        "clientId", clientId,
                        "clientSecret", clientSecret));

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("access_token")) {
            throw new IllegalStateException("Zoho token refresh failed: " + body);
        }

        cachedAccessToken = (String) body.get("access_token");
        int expiresIn = body.get("expires_in") instanceof Number
                ? ((Number) body.get("expires_in")).intValue()
                : 3600;
        tokenExpiry = Instant.now().plusSeconds(expiresIn);
    }
}
