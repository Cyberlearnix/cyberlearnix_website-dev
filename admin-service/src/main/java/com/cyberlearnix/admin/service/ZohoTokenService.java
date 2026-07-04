package com.cyberlearnix.admin.service;

import com.cyberlearnix.admin.entity.SiteSetting;
import com.cyberlearnix.admin.repository.SiteSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Manages the Zoho OAuth 2.0 access token lifecycle.
 *
 * Zoho access tokens expire after 1 hour. This service caches the token
 * and refreshes it automatically when it is within 60 seconds of expiry.
 *
 * Zoho rotates the refresh token on every use. The latest refresh token is
 * persisted in the site_settings table (key: zoho_refresh_token) so it
 * survives pod restarts. Falls back to the application.properties value if
 * the DB entry is absent.
 */
@Service
public class ZohoTokenService {

    private static final String DB_KEY = "zoho_refresh_token";
    private static final String DB_GROUP = "zoho_oauth";

    @Value("${zoho.client-id}")
    private String clientId;

    @Value("${zoho.client-secret}")
    private String clientSecret;

    @Value("${zoho.refresh-token}")
    private String refreshToken;

    @Value("${zoho.accounts-url:https://accounts.zoho.com}")
    private String accountsUrl;

    private final RestTemplate restTemplate;

    @Autowired
    private SiteSettingRepository siteSettingRepository;

    private String cachedAccessToken;
    private Instant tokenExpiry = Instant.EPOCH;
    // Latest refresh token for this pod session (updated after each rotation)
    private String currentRefreshToken;
    // True once we have attempted to load the token from the DB
    private boolean dbTokenLoaded = false;

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

    /**
     * Returns the refresh token to use, consulting DB on first call.
     * Priority: in-memory (post-rotation) > DB (persisted) > application.properties
     */
    private String getEffectiveRefreshToken() {
        if (currentRefreshToken == null && !dbTokenLoaded) {
            dbTokenLoaded = true;
            siteSettingRepository.findBySettingKey(DB_KEY)
                    .map(SiteSetting::getSettingValue)
                    .filter(t -> t != null && !t.isBlank())
                    .ifPresent(t -> currentRefreshToken = t);
        }
        return currentRefreshToken != null ? currentRefreshToken : refreshToken;
    }

    @Transactional
    public void persistRefreshToken(String token) {
        SiteSetting setting = siteSettingRepository.findBySettingKey(DB_KEY)
                .orElseGet(SiteSetting::new);
        setting.setSettingKey(DB_KEY);
        setting.setSettingValue(token);
        setting.setSettingGroup(DB_GROUP);
        setting.setIsActive(true);
        siteSettingRepository.save(setting);
    }

    /**
     * Seeds a new refresh token (and optionally access token) obtained outside
     * the normal refresh flow — e.g. from an auth-code exchange done by the admin.
     * Clears the cached access token so the next call triggers a fresh refresh.
     */
    public synchronized void seedToken(String newRefreshToken, String newAccessToken, int expiresIn) {
        currentRefreshToken = newRefreshToken;
        dbTokenLoaded = false; // force DB re-read on next refresh so we pick up the persisted token
        persistRefreshToken(newRefreshToken);
        if (newAccessToken != null && !newAccessToken.isBlank()) {
            cachedAccessToken = newAccessToken;
            tokenExpiry = Instant.now().plusSeconds(expiresIn > 0 ? expiresIn : 3600);
        } else {
            // Force refresh on next getAccessToken() call
            cachedAccessToken = null;
            tokenExpiry = Instant.EPOCH;
        }
    }

    /**
     * Exchanges a Zoho authorization code for tokens and seeds them.
     * The authCode must be obtained from Zoho API Console → Self Client.
     */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> exchangeAuthCode(String authCode) {
        String url = accountsUrl
                + "/oauth/v2/token"
                + "?code={code}"
                + "&client_id={clientId}"
                + "&client_secret={clientSecret}"
                + "&redirect_uri=urn:ietf:wg:oauth:2.0:oob"
                + "&grant_type=authorization_code";

        ResponseEntity<Map> response = restTemplate.postForEntity(
                url, null, Map.class,
                Map.of("code", authCode, "clientId", clientId, "clientSecret", clientSecret));

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("access_token")) {
            throw new IllegalArgumentException("Zoho auth-code exchange failed: " + body);
        }

        String newRefreshToken = (String) body.get("refresh_token");
        String newAccessToken = (String) body.get("access_token");
        int expiresIn = body.get("expires_in") instanceof Number
                ? ((Number) body.get("expires_in")).intValue() : 3600;

        seedToken(newRefreshToken, newAccessToken, expiresIn);
        return body;
    }

    @SuppressWarnings("unchecked")
    private void doRefresh() {
        String tokenToUse = getEffectiveRefreshToken();

        if (tokenToUse == null || tokenToUse.isBlank()) {
            throw new IllegalStateException(
                "Zoho refresh token is not configured. Run the token seed script to re-authenticate.");
        }

        String url = accountsUrl
                + "/oauth/v2/token"
                + "?refresh_token={refreshToken}"
                + "&client_id={clientId}"
                + "&client_secret={clientSecret}"
                + "&grant_type=refresh_token";

        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(
                    url, null, Map.class,
                    Map.of("refreshToken", tokenToUse,
                            "clientId", clientId,
                            "clientSecret", clientSecret));
        } catch (HttpClientErrorException ex) {
            throw new IllegalStateException(
                "Zoho token refresh failed (HTTP " + ex.getStatusCode().value() + "): "
                + ex.getResponseBodyAsString()
                + ". The refresh token may have expired — please re-run the Zoho token seed.");
        } catch (HttpServerErrorException ex) {
            throw new IllegalStateException(
                "Zoho accounts service returned a server error (HTTP "
                + ex.getStatusCode().value() + ") — please try again later.");
        }

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("access_token")) {
            String errorDesc = body != null ? String.valueOf(body.getOrDefault("error_description", body)) : "null";
            throw new IllegalStateException(
                "Zoho token refresh failed — no access_token in response. "
                + "Error: " + errorDesc
                + ". Please re-run the Zoho token seed script (node seed-zoho-token.js).");
        }

        cachedAccessToken = (String) body.get("access_token");
        int expiresIn = body.get("expires_in") instanceof Number
                ? ((Number) body.get("expires_in")).intValue()
                : 3600;
        tokenExpiry = Instant.now().plusSeconds(expiresIn);

        // Persist rotated refresh token to DB so it survives pod restarts
        String newRefreshToken = (String) body.get("refresh_token");
        if (newRefreshToken != null && !newRefreshToken.isBlank()) {
            currentRefreshToken = newRefreshToken;
            persistRefreshToken(newRefreshToken);
        }
    }
}
