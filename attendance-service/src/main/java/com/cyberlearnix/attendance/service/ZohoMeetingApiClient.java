package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.entity.Meeting;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Zoho Meeting REST API client.
 * Handles OAuth token refresh and meeting CRUD.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZohoMeetingApiClient {

    private final RestTemplate restTemplate;

    @Value("${zoho.meeting.client-id:}")
    private String clientId;

    @Value("${zoho.meeting.client-secret:}")
    private String clientSecret;

    @Value("${zoho.meeting.refresh-token:}")
    private String refreshToken;

    @Value("${zoho.meeting.accounts-url:https://accounts.zoho.in/oauth/v2}")
    private String accountsUrl;

    @Value("${zoho.meeting.api-url:https://meeting.zoho.in/api/v1}")
    private String apiUrl;

    private volatile String accessToken;
    private volatile long tokenExpiresAt = 0;

    public ZohoMeetingResponse createMeeting(Meeting meeting) {
        ensureAccessToken();
        Map<String, Object> body = new HashMap<>();
        body.put("topic", meeting.getTitle());
        body.put("agenda", meeting.getDescription() != null ? meeting.getDescription() : "");
        body.put("start_time", meeting.getScheduledStart().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        if (meeting.getScheduledStart() != null && meeting.getScheduledEnd() != null) {
            long duration = java.time.Duration.between(meeting.getScheduledStart(), meeting.getScheduledEnd()).toMinutes();
            body.put("duration", duration);
        }

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                apiUrl + "/meetings", HttpMethod.POST, req, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> data = response.getBody();
                ZohoMeetingResponse resp = new ZohoMeetingResponse();
                resp.setMeetingId(String.valueOf(data.get("meeting_id")));
                resp.setJoinUrl(String.valueOf(data.getOrDefault("join_url", "")));
                resp.setPassword(String.valueOf(data.getOrDefault("password", "")));
                return resp;
            }
        } catch (Exception e) {
            log.error("Zoho create meeting failed: {}", e.getMessage());
        }
        return null;
    }

    public void deleteMeeting(String zohoMeetingId) {
        ensureAccessToken();
        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> req = new HttpEntity<>(headers);
        try {
            restTemplate.exchange(apiUrl + "/meetings/" + zohoMeetingId, HttpMethod.DELETE, req, Void.class);
        } catch (Exception e) {
            log.error("Zoho delete meeting failed: {}", e.getMessage());
        }
    }

    private synchronized void ensureAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiresAt - 60_000) return;
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Zoho refresh token not configured — skipping OAuth token refresh");
            return;
        }
        try {
            String url = accountsUrl + "/token?grant_type=refresh_token&client_id=" + clientId
                + "&client_secret=" + clientSecret + "&refresh_token=" + refreshToken;
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, null, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                accessToken = (String) resp.getBody().get("access_token");
                Integer expiresIn = (Integer) resp.getBody().getOrDefault("expires_in", 3600);
                tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
                log.info("Zoho access token refreshed successfully");
            }
        } catch (Exception e) {
            log.error("Failed to refresh Zoho access token: {}", e.getMessage());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return headers;
    }

    @Data
    public static class ZohoMeetingResponse {
        private String meetingId;
        private String joinUrl;
        private String password;
    }
}
