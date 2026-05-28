package com.cyberlearnix.admin.service;

import com.cyberlearnix.admin.dto.PanelistDto;
import com.cyberlearnix.admin.dto.TeamsMeetingRequest;
import com.cyberlearnix.admin.dto.TeamsMeetingResponse;
import com.cyberlearnix.admin.entity.TeamsMeeting;
import com.cyberlearnix.admin.repository.TeamsMeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.core.type.TypeReference;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * Schedules and manages online meetings via the Zoho Meeting REST API v2.
 *
 * Zoho Meeting Professional plan supports up to 250 attendees, unlimited meetings, up to 24-hour sessions, and cloud recording.
 * Participants receive a joinUrl and do NOT need a Zoho account to join.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamsService {

    private static final DateTimeFormatter ZOHO_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a", Locale.ENGLISH);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final ZohoTokenService tokenService;
    private final TeamsMeetingRepository meetingRepository;
    private final com.cyberlearnix.admin.client.NotificationClient notificationClient;

    @Value("${zoho.org-id}")
    private String orgId;

    @Value("${zoho.host-zuid}")
    private String hostZuid;

    @Value("${zoho.meeting-api-url:https://meeting.zoho.in/api/v2}")
    private String meetingApiUrl;

    @Value("${zoho.accounts-url:https://accounts.zoho.in}")
    private String accountsUrl;

    /**
     * Auto-discovers the Zoho orgId at startup when zoho.org-id is not configured.
     *
     * Strategy:
     *  1. Call Zoho Accounts OAuth user-info → use the returned ZAUID as orgId.
     *  2. If that fails, fall back to hostZuid (works for personal/free accounts).
     *
     * Once you see "[Zoho] Resolved orgId: <value>" in the logs, copy that value into
     * application.properties as  zoho.org-id=<value>  to skip discovery on future starts.
     */
    @PostConstruct
    @SuppressWarnings("unchecked")
    private void initOrgId() {
        if (!"0".equals(orgId)) {
            log.info("[Zoho] Using configured orgId: {}", orgId);
            return;
        }
        log.warn("[Zoho] zoho.org-id not configured (value=0). Attempting auto-discovery via portals.json ...");

        // --- Strategy 1: GET /api/v2/portals.json — canonical Zoho Meeting org lookup ---
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    meetingApiUrl + "/portals.json",
                    HttpMethod.GET,
                    new HttpEntity<>(zohoHeaders()),
                    Map.class);
            Map<String, Object> body = resp.getBody();
            log.info("[Zoho] portals.json response: {}", body);
            if (body != null) {
                // Response shape: { "portals": [ { "orgId": "...", ... } ] }   OR   { "organization": { "orgId": "..." } }
                List<Map<String, Object>> portals =
                        (List<Map<String, Object>>) body.get("portals");
                if (portals != null && !portals.isEmpty()) {
                    Object discovered = portals.get(0).get("orgId");
                    if (discovered == null) discovered = portals.get(0).get("id");
                    if (discovered != null) {
                        orgId = String.valueOf(discovered);
                        log.info("[Zoho] Resolved orgId={} from portals.json. " +
                                "Add  zoho.org-id={}  to application.properties.", orgId, orgId);
                        return;
                    }
                }
                // fallback: organization key
                Map<String, Object> org = (Map<String, Object>) body.get("organization");
                if (org != null) {
                    Object discovered = org.get("orgId");
                    if (discovered == null) discovered = org.get("id");
                    if (discovered != null) {
                        orgId = String.valueOf(discovered);
                        log.info("[Zoho] Resolved orgId={} from portals.json/organization. " +
                                "Add  zoho.org-id={}  to application.properties.", orgId, orgId);
                        return;
                    }
                }
                log.warn("[Zoho] portals.json returned no recognisable orgId field: {}", body);
            }
        } catch (Exception e) {
            log.warn("[Zoho] portals.json discovery failed: {}", e.getMessage());
        }

        // --- Strategy 2: fall back to hostZuid (individual Zoho accounts: ZAUID == orgId) ---
        if (hostZuid != null && !hostZuid.isBlank() && !"0".equals(hostZuid)) {
            orgId = hostZuid;
            log.warn("[Zoho] Falling back to zoho.host-zuid ({}) as orgId. " +
                    "If API calls still fail with 'Invalid Org Id', check the portals.json log " +
                    "above for the real value and set  zoho.org-id=<value>  in application.properties.",
                    orgId);
        } else {
            log.error("[Zoho] Could not determine orgId. " +
                    "Set  zoho.org-id=<your-zoho-org-id>  in application.properties.");
        }
    }

    /**
     * Schedules a new Zoho meeting and persists the record locally.
     *
     * @param request meeting details from the admin
     * @param adminId Cyberlearnix admin user ID
     * @return saved meeting including the joinUrl for participants
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public TeamsMeetingResponse scheduleMeeting(TeamsMeetingRequest request, String adminId) {
        if (!request.getEndDateTime().isAfter(request.getStartDateTime())) {
            throw new IllegalArgumentException("End date/time must be after start date/time");
        }

        long durationMinutes = ChronoUnit.MINUTES.between(
                request.getStartDateTime(), request.getEndDateTime());

        // Build Zoho session payload
        Map<String, Object> session = new HashMap<>();
        session.put("topic", request.getSubject());
        session.put("agenda", request.getDescription() != null ? request.getDescription() : "");
        session.put("startTime", request.getStartDateTime().format(ZOHO_DATE_FMT));
        session.put("duration", durationMinutes * 60L * 1000L); // Zoho expects milliseconds
        session.put("timezone", "Asia/Kolkata");
        session.put("presenter", hostZuid);

        applyRecurringFields(session, request);

        Map<String, Object> body = Map.of("session", session);
        log.info("Zoho create-meeting payload: {}", session);

        // POST /api/v2/{orgId}/sessions.json  (pure JSON body)
        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(
                    meetingApiUrl + "/{orgId}/sessions.json",
                    HttpMethod.POST,
                    new HttpEntity<>(body, zohoHeaders()),
                    Map.class,
                    orgId);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            throw zohoError(ex);
        }

        Map<String, Object> created = (Map<String, Object>)
                extractBody(response).get("session");

        String sessionKey = String.valueOf(created.get("meetingKey"));
        String joinUrl    = (String) created.get("joinLink");
        String password   = created.get("password") != null ? String.valueOf(created.get("password")) : null;

        // Invite panelists via the dedicated endpoint — this is what triggers Zoho's invitation emails.
        // Embedding panelists inside the session creation body does NOT send emails.
        sendPanelistInvites(sessionKey, request.getInvitees());

        // Persist locally
        TeamsMeeting entity = new TeamsMeeting();
        entity.setSubject(request.getSubject());
        entity.setDescription(request.getDescription());
        entity.setStartDateTime(request.getStartDateTime());
        entity.setEndDateTime(request.getEndDateTime());
        entity.setOrganizerUserId(orgId);
        entity.setGraphMeetingId(sessionKey);   // reused column stores Zoho sessionKey
        entity.setJoinUrl(joinUrl);
        entity.setPassword(password);
        entity.setRecurring(false);
        entity.setRepeatType(null);
        entity.setRepeatEvery(null);
        entity.setRecurrenceEndDate(null);
        entity.setCreatedBy(adminId);
        entity.setStatus("SCHEDULED");
        entity.setCourseId(request.getCourseId());
        entity.setBatchId(request.getBatchId());
        entity.setInviteesJson(serializeInvitees(request.getInvitees()));

        TeamsMeetingResponse saved = toResponse(meetingRepository.save(entity));

        // Notify enrolled students about the new live session
        try {
            if (request.getCourseId() != null) {
                java.util.Map<String, Object> notifReq = new java.util.HashMap<>();
                notifReq.put("courseId", request.getCourseId());
                notifReq.put("type", "LIVE_SESSION");
                notifReq.put("title", "New Live Session Scheduled: " + request.getSubject());
                notifReq.put("body", "A live session has been scheduled for your course. "
                        + "Starts at " + request.getStartDateTime().format(
                                java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a", java.util.Locale.ENGLISH)) + ".");
                notifReq.put("link", "/meetings");
                notificationClient.createInAppNotification("true", notifReq);
            }
        } catch (Exception ex) {
            log.warn("Failed to create LIVE_SESSION in-app notification: {}", ex.getMessage());
        }

        return saved;
    }

    /**
     * Returns meetings for a specific course (student-facing).
     */
    public List<TeamsMeetingResponse> getMeetingsByCourseId(Long courseId) {
        return meetingRepository.findAllByCourseIdOrderByStartDateTimeAsc(courseId)
                .stream()
                .filter(m -> !"CANCELLED".equals(m.getStatus()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns all meetings, optionally filtered by status.
     */
    public List<TeamsMeetingResponse> getMeetings(String status) {
        List<TeamsMeeting> meetings = (status != null && !status.isBlank())
                ? meetingRepository.findAllByStatusOrderByStartDateTimeAsc(status.toUpperCase())
                : meetingRepository.findAll();
        return meetings.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Returns a single meeting by local DB id.
     */
    public TeamsMeetingResponse getMeeting(Long id) {
        return toResponse(findById(id));
    }

    /**
     * Updates subject, description, start/end time of a scheduled meeting.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public TeamsMeetingResponse updateMeeting(Long id, TeamsMeetingRequest request) {
        TeamsMeeting meeting = findById(id);

        if ("CANCELLED".equals(meeting.getStatus())) {
            throw new IllegalStateException("Cannot update a cancelled meeting");
        }
        if (!request.getEndDateTime().isAfter(request.getStartDateTime())) {
            throw new IllegalArgumentException("End date/time must be after start date/time");
        }

        long durationMinutes = ChronoUnit.MINUTES.between(
                request.getStartDateTime(), request.getEndDateTime());

        Map<String, Object> session = new HashMap<>();
        session.put("topic", request.getSubject());
        session.put("agenda", request.getDescription() != null ? request.getDescription() : "");
        session.put("startTime", request.getStartDateTime().format(ZOHO_DATE_FMT));
        session.put("duration", durationMinutes * 60L * 1000L); // Zoho expects milliseconds
        session.put("timezone", "Asia/Kolkata");
        session.put("presenter", hostZuid);
        applyRecurringFields(session, request);

        Map<String, Object> body = Map.of("session", session);
        log.info("Zoho update-meeting payload: {}", session);

        // PUT /api/v2/{orgId}/sessions/{sessionKey}.json
        // If Zoho returns 403/INVALID_ORG_ID the session no longer exists in Zoho
        // (e.g. expired past meeting) — re-create it so we get a fresh joinUrl.
        boolean recreated = false;
        try {
            restTemplate.exchange(
                    meetingApiUrl + "/{orgId}/sessions/{sessionKey}.json",
                    HttpMethod.PUT,
                    new HttpEntity<>(body, zohoHeaders()),
                    Map.class,
                    orgId,
                    meeting.getGraphMeetingId());
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            String raw = ex.getResponseBodyAsString();
            boolean isGone = raw.contains("INVALID_ORG_ID") ||
                             raw.contains("Invalid Org Id") ||
                             raw.contains("SESSION_NOT_FOUND") ||
                             raw.contains("Invalid session");
            if (!isGone) {
                throw zohoError(ex);
            }
            log.warn("[Zoho] Session {} not found in Zoho ({}). Re-creating meeting in Zoho.",
                    meeting.getGraphMeetingId(), ex.getStatusCode());
            // Re-create in Zoho
            try {
                ResponseEntity<Map> resp = restTemplate.exchange(
                        meetingApiUrl + "/{orgId}/sessions.json",
                        HttpMethod.POST,
                        new HttpEntity<>(body, zohoHeaders()),
                        Map.class,
                        orgId);
                @SuppressWarnings("unchecked")
                Map<String, Object> created = (Map<String, Object>) extractBody(resp).get("session");
                meeting.setGraphMeetingId(String.valueOf(created.get("meetingKey")));
                meeting.setJoinUrl((String) created.get("joinLink"));
                if (created.get("password") != null) {
                    meeting.setPassword(String.valueOf(created.get("password")));
                }
                recreated = true;
                log.info("[Zoho] Re-created meeting. New sessionKey={}, joinUrl={}",
                        meeting.getGraphMeetingId(), meeting.getJoinUrl());
            } catch (HttpClientErrorException | HttpServerErrorException reEx) {
                throw zohoError(reEx);
            }
        }

        meeting.setSubject(request.getSubject());
        meeting.setDescription(request.getDescription());
        meeting.setStartDateTime(request.getStartDateTime());
        meeting.setEndDateTime(request.getEndDateTime());
        meeting.setRecurring(false);
        meeting.setRepeatType(null);
        meeting.setRepeatEvery(null);
        meeting.setRecurrenceEndDate(null);
        meeting.setCourseId(request.getCourseId());
        meeting.setBatchId(request.getBatchId());
        // Only update invitees when the field is explicitly included in the request.
        // - Omit "invitees" in JSON  → existing invitee list is preserved
        // - Send "invitees": []       → all invitees removed
        // - Send "invitees": [...]    → diff applied: old emails removed, new emails added
        if (request.getInvitees() != null) {
            List<PanelistDto> oldInvitees = deserializeInvitees(meeting.getInviteesJson());
            meeting.setInviteesJson(serializeInvitees(request.getInvitees()));
            syncPanelists(meeting.getGraphMeetingId(), oldInvitees, request.getInvitees());
        }
        if (!recreated) {
            // join URL unchanged on a normal update
        }

        return toResponse(meetingRepository.save(meeting));
    }

    private void applyRecurringFields(Map<String, Object> session, TeamsMeetingRequest request) {
        if (!request.isRecurring()) {
            return;
        }

        log.warn("Recurring meeting was requested, but current Zoho integration supports one-time meetings only. Ignoring recurrence fields.");
    }
    /**
     * Cancels a meeting in Zoho and marks it cancelled locally.
     */
    @Transactional
    public void cancelMeeting(Long id) {
        TeamsMeeting meeting = findById(id);

        if ("CANCELLED".equals(meeting.getStatus())) {
            throw new IllegalStateException("Meeting is already cancelled");
        }

        // DELETE /api/v2/{orgId}/sessions/{sessionKey}.json
        // If the session no longer exists in Zoho, still mark as cancelled locally.
        try {
            restTemplate.exchange(
                    meetingApiUrl + "/{orgId}/sessions/{sessionKey}.json",
                    HttpMethod.DELETE,
                    new HttpEntity<>(zohoHeaders()),
                    Void.class,
                    orgId,
                    meeting.getGraphMeetingId());
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            String raw = ex.getResponseBodyAsString();
            boolean isGone = raw.contains("INVALID_ORG_ID") ||
                             raw.contains("Invalid Org Id") ||
                             raw.contains("SESSION_NOT_FOUND") ||
                             raw.contains("Invalid session");
            if (!isGone) {
                throw zohoError(ex);
            }
            log.warn("[Zoho] Session {} not found in Zoho during cancel — marking cancelled locally.",
                    meeting.getGraphMeetingId());
        }

        meeting.setStatus("CANCELLED");
        meetingRepository.save(meeting);
    }

    // ---- helpers ----

    /** Extracts a clean error message from a Zoho API HTTP error response. */
    private RuntimeException zohoError(RestClientResponseException ex) {
        String rawBody = ex.getResponseBodyAsString();
        log.error("Zoho raw error response [{}]: {}", ex.getStatusCode(), rawBody);
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(rawBody);
            JsonNode error = root.path("error");
            String code    = error.path("code").asText("");
            String message = error.path("message").asText("");
            String param   = error.path("parameter_name").asText("");
            String detail  = param.isEmpty() ? message : message + " (field: " + param + ")";
            String full    = detail.isBlank() ? "Zoho API error " + code : detail;
            return new RuntimeException(full);
        } catch (Exception ignored) {
            // rawBody may be an HTML maintenance page — never forward it to the client
            String statusCode = ex.getStatusCode().toString();
            boolean isServerError = rawBody != null && (rawBody.trim().startsWith("<") || rawBody.length() > 500);
            String clientMsg = isServerError
                    ? "Zoho Meeting service is temporarily unavailable (HTTP " + statusCode + "). Please try again in a few minutes."
                    : "Zoho API error [" + statusCode + "]: " + rawBody;
            return new RuntimeException(clientMsg);
        }
    }

    /** Headers for create/update requests */
    private HttpHeaders zohoFormHeaders() {
        return zohoHeaders();
    }

    /** Headers for JSON requests (delete) */
    private HttpHeaders zohoHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + tokenService.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractBody(ResponseEntity<Map> response) {
        if (response.getBody() == null) {
            throw new IllegalStateException("Empty response from Zoho Meeting API");
        }
        return response.getBody();
    }

    private TeamsMeeting findById(Long id) {
        return meetingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + id));
    }

    private TeamsMeetingResponse toResponse(TeamsMeeting m) {
        return TeamsMeetingResponse.builder()
                .id(m.getId())
                .subject(m.getSubject())
                .description(m.getDescription())
                .startDateTime(m.getStartDateTime())
                .endDateTime(m.getEndDateTime())
                .courseId(m.getCourseId())
                .batchId(m.getBatchId())
                .meetingId(m.getGraphMeetingId())
                .joinUrl(m.getJoinUrl())
                .password(m.getPassword())
                .duration(formatDuration(m.getStartDateTime(), m.getEndDateTime()))
                .status(m.getStatus())
                .createdBy(m.getCreatedBy())
                .createdAt(m.getCreatedAt())
                .invitees(deserializeInvitees(m.getInviteesJson()))
                .recurring(m.isRecurring())
                .repeatType(m.getRepeatType())
                .repeatEvery(m.getRepeatEvery())
                .recurrenceEndDate(m.getRecurrenceEndDate())
                .build();
    }

    private String formatDuration(LocalDateTime start, LocalDateTime end) {
        long totalMinutes = ChronoUnit.MINUTES.between(start, end);
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) return minutes + " min";
        if (minutes == 0) return hours + " hr" + (hours > 1 ? "s" : "");
        return hours + " hr" + (hours > 1 ? "s" : "") + " " + minutes + " min";
    }

    /**
     * On CREATE: sends Zoho invitation emails to all invitees.
     * Calls POST /api/v2/{orgId}/sessions/{sessionKey}/panelists.json
     *
     * Embedding panelists inside the session creation body does NOT send emails—
     * this separate call is required.
     */
    private void sendPanelistInvites(String sessionKey, List<PanelistDto> invitees) {
        if (invitees == null || invitees.isEmpty()) return;

        List<Map<String, String>> panelists = invitees.stream()
                .map(p -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("email", p.getEmail());
                    if (p.getName() != null && !p.getName().isBlank()) m.put("name", p.getName());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = Map.of("panelists", panelists);
        log.info("[Zoho] Inviting {} panelist(s) for session {}: {}", panelists.size(), sessionKey, panelists);

        try {
            restTemplate.exchange(
                    meetingApiUrl + "/{orgId}/sessions/{sessionKey}/panelists.json",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, zohoHeaders()),
                    Map.class,
                    orgId,
                    sessionKey);
            log.info("[Zoho] Invitation emails sent successfully for session {}", sessionKey);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            // Non-fatal — meeting is created; log and continue.
            log.error("[Zoho] Failed to send panelist invitations for session {} ({}): {}",
                    sessionKey, ex.getStatusCode(), ex.getResponseBodyAsString());
        }
    }

    /**
     * On UPDATE: diffs the old and new invitee lists, then:
     *  - POSTs newly added emails → Zoho sends invite emails
     *  - DELETEs removed emails  → Zoho revokes their access
     *
     * Add: POST /api/v2/{orgId}/sessions/{sessionKey}/panelists.json
     * Remove: DELETE /api/v2/{orgId}/sessions/{sessionKey}/panelists/{email}.json
     */
    private void syncPanelists(String sessionKey,
                               List<PanelistDto> oldList,
                               List<PanelistDto> newList) {

        Set<String> oldEmails = oldList.stream()
                .map(p -> p.getEmail().toLowerCase())
                .collect(Collectors.toSet());
        Set<String> newEmails = newList.stream()
                .map(p -> p.getEmail().toLowerCase())
                .collect(Collectors.toSet());

        // --- Add new invitees ---
        List<PanelistDto> toAdd = newList.stream()
                .filter(p -> !oldEmails.contains(p.getEmail().toLowerCase()))
                .collect(Collectors.toList());

        if (!toAdd.isEmpty()) {
            List<Map<String, String>> panelistPayload = toAdd.stream()
                    .map(p -> {
                        Map<String, String> m = new HashMap<>();
                        m.put("email", p.getEmail());
                        if (p.getName() != null && !p.getName().isBlank()) m.put("name", p.getName());
                        return m;
                    })
                    .collect(Collectors.toList());

            log.info("[Zoho] Adding {} panelist(s) to session {}: {}", toAdd.size(), sessionKey, panelistPayload);
            try {
                restTemplate.exchange(
                        meetingApiUrl + "/{orgId}/sessions/{sessionKey}/panelists.json",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("panelists", panelistPayload), zohoHeaders()),
                        Map.class,
                        orgId,
                        sessionKey);
                log.info("[Zoho] Invite emails sent to {} new panelist(s) for session {}", toAdd.size(), sessionKey);
            } catch (HttpClientErrorException | HttpServerErrorException ex) {
                log.error("[Zoho] Failed to add panelists for session {} ({}): {}",
                        sessionKey, ex.getStatusCode(), ex.getResponseBodyAsString());
            }
        }

        // --- Remove deleted invitees ---
        List<String> toRemove = oldList.stream()
                .map(PanelistDto::getEmail)
                .filter(email -> !newEmails.contains(email.toLowerCase()))
                .collect(Collectors.toList());

        for (String email : toRemove) {
            log.info("[Zoho] Removing panelist {} from session {}", email, sessionKey);
            try {
                restTemplate.exchange(
                        meetingApiUrl + "/{orgId}/sessions/{sessionKey}/panelists/{email}.json",
                        HttpMethod.DELETE,
                        new HttpEntity<>(zohoHeaders()),
                        Void.class,
                        orgId,
                        sessionKey,
                        email);
                log.info("[Zoho] Removed panelist {} from session {}", email, sessionKey);
            } catch (HttpClientErrorException | HttpServerErrorException ex) {
                log.warn("[Zoho] Could not remove panelist {} from session {} ({}): {}",
                        email, sessionKey, ex.getStatusCode(), ex.getResponseBodyAsString());
            }
        }

        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            log.info("[Zoho] No panelist changes for session {} — invitee list unchanged.", sessionKey);
        }
    }

    /** Serializes a list of PanelistDto to a JSON string for DB storage. */
    private String serializeInvitees(List<PanelistDto> invitees) {
        if (invitees == null || invitees.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(invitees);
        } catch (Exception e) {
            log.warn("Failed to serialize invitees: {}", e.getMessage());
            return null;
        }
    }

    /** Deserializes the JSON string from DB back to a list of PanelistDto. */
    private List<PanelistDto> deserializeInvitees(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, new TypeReference<List<PanelistDto>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize invitees: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}

