package com.cyberlearnix.admin.service;

import com.cyberlearnix.admin.dto.ParticipantDto;
import com.cyberlearnix.admin.entity.MeetingParticipant;
import com.cyberlearnix.admin.entity.TeamsMeeting;
import com.cyberlearnix.admin.repository.MeetingParticipantRepository;
import com.cyberlearnix.admin.repository.TeamsMeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Syncs Zoho Meeting sessions + attendance into the local admin database.
 *
 * ATTENDANCE ENDPOINT (per Zoho's Meeting Participant Report docs):
 *   GET https://meeting.zoho.in/api/v2/{zsoid}/participant/{meetingKey}.json?index=1&count=100
 *   OAuth scope: ZohoMeeting.meeting.READ  (included in ZohoMeeting.meeting.ALL)
 *
 * Reference: https://www.zoho.com/meeting/api-integration/meeting-api/participant-report.html
 *
 * Works for REGULAR MEETINGS (not webinars). Returns attendance data for any
 * meeting where participants actually joined.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZohoSyncService {

    private static final DateTimeFormatter ZOHO_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a", Locale.ENGLISH);

    private static final long END_BUFFER_MINUTES = 5;
    private static final long MAX_BACKFILL_DAYS = 30;

    private static final List<String> SESSION_TYPES = List.of("ALL", "UPCOMING", "PAST");

    /**
     * Meeting Participant Report endpoint (official Zoho docs).
     * Returns all participants who joined a meeting.
     */
    private static final String ATTENDEE_ENDPOINT =
            "/{orgId}/participant/{sessionKey}.json?index=1&count=100";

    private final RestTemplate restTemplate;
    private final ZohoTokenService tokenService;
    private final TeamsMeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;

    @Value("${zoho.org-id}")
    private String orgId;

    @Value("${zoho.meeting-api-url:https://meeting.zoho.in/api/v2}")
    private String meetingApiUrl;

    // ─── SCHEDULERS ─────────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${zoho.sync.meetings-interval-ms:600000}",
            initialDelayString = "${zoho.sync.meetings-initial-delay-ms:60000}")
    public void syncMeetings() {
        log.info("[ZohoSync] Starting meeting sync...");
        try {
            int upserted = syncMeetingsFromZoho();
            log.info("[ZohoSync] Meeting sync complete — {} meeting(s) upserted.", upserted);
        } catch (Exception e) {
            log.error("[ZohoSync] Meeting sync FAILED: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${zoho.sync.participants-interval-ms:900000}",
            initialDelayString = "${zoho.sync.participants-initial-delay-ms:120000}")
    public void syncParticipants() {
        log.info("[ZohoSync] Starting participant sync...");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(END_BUFFER_MINUTES);
        LocalDateTime oldest = now.minusDays(MAX_BACKFILL_DAYS);

        List<TeamsMeeting> pending = meetingRepository.findForParticipantSync(cutoff, oldest);
        log.info("[ZohoSync] {} meeting(s) need participant sync.", pending.size());

        int synced = 0;
        for (TeamsMeeting m : pending) {
            try {
                int count = fetchAndStoreParticipants(m);
                log.info("[ZohoSync] Meeting #{} ({}): synced {} participant(s).",
                        m.getId(), m.getGraphMeetingId(), count);
                synced++;
            } catch (Exception e) {
                log.error("[ZohoSync] Meeting #{} participant sync FAILED: {}",
                        m.getId(), e.getMessage());
            }
        }
        log.info("[ZohoSync] Participant sync complete — {}/{} meeting(s) synced.",
                synced, pending.size());
    }

    // ─── PUBLIC API ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ParticipantDto> getParticipants(Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new IllegalArgumentException("Meeting not found: " + meetingId);
        }
        return participantRepository.findByMeetingIdOrderByJoinTimeAsc(meetingId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public int triggerParticipantSync(Long meetingId) {
        TeamsMeeting m = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));
        return fetchAndStoreParticipants(m);
    }

    public int triggerMeetingSync() {
        return syncMeetingsFromZoho();
    }

    public Map<String, Object> debugAttendanceEndpoints(Long meetingId) {
        TeamsMeeting m = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));
        String sessionKey = m.getGraphMeetingId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("meetingId", meetingId);
        result.put("sessionKey", sessionKey);
        result.put("subject", m.getSubject());
        result.put("endpoint", meetingApiUrl + ATTENDEE_ENDPOINT);

        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    meetingApiUrl + ATTENDEE_ENDPOINT,
                    HttpMethod.GET,
                    new HttpEntity<>(zohoHeaders()),
                    Map.class,
                    orgId, sessionKey);
            Map<String, Object> body = resp.getBody();
            result.put("status", resp.getStatusCode().value());
            result.put("topLevelKeys", body != null ? body.keySet() : null);
            result.put("body", body);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            result.put("status", ex.getStatusCode().value());
            result.put("error", ex.getResponseBodyAsString());
        } catch (Exception ex) {
            result.put("status", "EXCEPTION");
            result.put("error", ex.getMessage());
        }
        return result;
    }

    public Map<String, Object> debugFetchZohoRaw(String sessionType) {
        boolean withFilter = sessionType != null && !sessionType.isBlank()
                && !"none".equalsIgnoreCase(sessionType);
        String url = withFilter
                ? meetingApiUrl + "/{orgId}/sessions.json?sessionType={sessionType}&index=1&count=10"
                : meetingApiUrl + "/{orgId}/sessions.json?index=1&count=10";
        try {
            ResponseEntity<Map> resp = withFilter
                    ? restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(zohoHeaders()), Map.class, orgId, sessionType)
                    : restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(zohoHeaders()), Map.class, orgId);
            Map<String, Object> body = resp.getBody();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", resp.getStatusCode().value());
            result.put("topLevelKeys", body != null ? body.keySet() : null);
            result.put("body", body);
            return result;
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", ex.getStatusCode().value());
            result.put("error", ex.getResponseBodyAsString());
            return result;
        }
    }

    // ─── MEETING SYNC ──────────────────────────────────────────────────────

    @Transactional
    protected int syncMeetingsFromZoho() {
        List<Map<String, Object>> allSessions = new ArrayList<>();
        boolean anyTypeSucceeded = false;

        for (String sessionType : SESSION_TYPES) {
            try {
                List<Map<String, Object>> got = fetchSessions(sessionType);
                if (got != null) {
                    anyTypeSucceeded = true;
                    allSessions.addAll(got);
                    log.info("[ZohoSync] sessionType={} fetched {} raw session(s).",
                            sessionType, got.size());
                    if ("ALL".equals(sessionType) && !got.isEmpty()) {
                        log.info("[ZohoSync] ALL filter succeeded — skipping other filters.");
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("[ZohoSync] sessionType={} failed: {}", sessionType, e.getMessage());
            }
        }

        if (!anyTypeSucceeded) {
            log.warn("[ZohoSync] All sessionType filters rejected. Trying no-filter...");
            try {
                List<Map<String, Object>> got = fetchSessions(null);
                if (got != null) {
                    allSessions.addAll(got);
                    log.info("[ZohoSync] no-filter fetched {} raw session(s).", got.size());
                }
            } catch (Exception e) {
                log.error("[ZohoSync] no-filter failed: {}", e.getMessage(), e);
            }
        }

        Map<String, Map<String, Object>> bestByKey = new HashMap<>();
        long nowMs = System.currentTimeMillis();
        for (Map<String, Object> s : allSessions) {
            String key = firstNonNull(
                    strVal(s.get("meetingKey")),
                    strVal(s.get("sessionKey")),
                    strVal(s.get("id")));
            if (key == null) continue;
            Map<String, Object> cur = bestByKey.get(key);
            if (cur == null || isBetterOccurrence(s, cur, nowMs)) {
                bestByKey.put(key, s);
            }
        }

        log.info("[ZohoSync] Deduped {} raw sessions into {} unique meetings.",
                allSessions.size(), bestByKey.size());

        int upserted = 0;
        for (Map<String, Object> s : bestByKey.values()) {
            try {
                if (upsertMeeting(s)) upserted++;
            } catch (Exception e) {
                log.warn("[ZohoSync] Skipped malformed session {}: {}",
                        s.get("meetingKey"), e.getMessage());
            }
        }
        return upserted;
    }

    private boolean isBetterOccurrence(Map<String, Object> candidate,
                                       Map<String, Object> current, long nowMs) {
        long cMs = toLong(firstNonNullObj(candidate.get("startTimeMillis"),
                candidate.get("startTimeMillisec")), 0L);
        long curMs = toLong(firstNonNullObj(current.get("startTimeMillis"),
                current.get("startTimeMillisec")), 0L);
        boolean cFuture = cMs >= nowMs;
        boolean curFuture = curMs >= nowMs;
        if (cFuture && !curFuture) return true;
        if (!cFuture && curFuture) return false;
        if (cFuture) return cMs < curMs;
        return cMs > curMs;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchSessions(String sessionType) {
        List<Map<String, Object>> all = new ArrayList<>();
        int index = 1;
        int pageSize = 100;

        while (true) {
            String url;
            ResponseEntity<Map> response;
            try {
                if (sessionType == null) {
                    url = meetingApiUrl + "/{orgId}/sessions.json?index={index}&count={count}";
                    response = restTemplate.exchange(url, HttpMethod.GET,
                            new HttpEntity<>(zohoHeaders()), Map.class,
                            orgId, index, pageSize);
                } else {
                    url = meetingApiUrl + "/{orgId}/sessions.json"
                            + "?sessionType={sessionType}&index={index}&count={count}";
                    response = restTemplate.exchange(url, HttpMethod.GET,
                            new HttpEntity<>(zohoHeaders()), Map.class,
                            orgId, sessionType, index, pageSize);
                }
            } catch (HttpClientErrorException ex) {
                String raw = ex.getResponseBodyAsString();
                if (ex.getStatusCode().value() == 400 &&
                        (raw.contains("PATTERN_NOT_MATCHED") || raw.contains("sessionType"))) {
                    log.warn("[ZohoSync] sessionType={} rejected by Zoho.", sessionType);
                    return null;
                }
                log.error("[ZohoSync] Fetch error {}: {}", ex.getStatusCode(), raw);
                break;
            } catch (HttpServerErrorException ex) {
                log.error("[ZohoSync] Zoho 5xx: {} {}",
                        ex.getStatusCode(), ex.getResponseBodyAsString());
                break;
            }

            Map<String, Object> body = response.getBody();
            if (body == null) break;

            List<Map<String, Object>> page = extractSessionList(body);
            if (page == null || page.isEmpty()) break;

            all.addAll(page);
            if (page.size() < pageSize) break;
            index += pageSize;
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSessionList(Map<String, Object> body) {
        for (String key : List.of(
                "session", "sessions", "meetings", "meeting",
                "upcomingMeetings", "pastMeetings",
                "scheduledMeetings", "completedMeetings",
                "data", "response")) {
            Object v = body.get(key);
            if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
                return (List<Map<String, Object>>) list;
            }
            if (v instanceof Map<?, ?> nested) {
                for (String innerKey : List.of("session", "sessions", "meetings")) {
                    Object inner = ((Map<String, Object>) nested).get(innerKey);
                    if (inner instanceof List<?> list && !list.isEmpty()
                            && list.get(0) instanceof Map) {
                        return (List<Map<String, Object>>) list;
                    }
                }
            }
        }
        return null;
    }

    private boolean upsertMeeting(Map<String, Object> s) {
        String sessionKey = firstNonNull(
                strVal(s.get("meetingKey")),
                strVal(s.get("sessionKey")),
                strVal(s.get("id")),
                strVal(s.get("sessionId")));
        if (sessionKey == null) return false;

        LocalDateTime startDt = null;
        long startMs = toLong(firstNonNullObj(s.get("startTimeMillis"),
                s.get("startTimeMillisec")), 0L);
        if (startMs > 0) {
            startDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs),
                    ZoneId.of("Asia/Kolkata"));
        }
        if (startDt == null) {
            String startRaw = firstNonNull(
                    strVal(s.get("startTime")),
                    strVal(s.get("scheduledStartTime")),
                    strVal(s.get("startDateTime")));
            startDt = parseZohoDate(startRaw);
        }
        if (startDt == null) {
            log.warn("[ZohoSync] Session {} has no parseable startTime, skipping.", sessionKey);
            return false;
        }

        long durationMs = toLong(firstNonNullObj(s.get("duration"), s.get("durationMs")), 0L);
        LocalDateTime endDt = startDt.plusSeconds(Math.max(durationMs / 1000, 0));

        String localStatus = mapStatus(strVal(s.get("status")));
        boolean isRecurring = Boolean.TRUE.equals(s.get("isRecurring"))
                || "true".equalsIgnoreCase(strVal(s.get("isRecurring")));

        Optional<TeamsMeeting> existing = meetingRepository.findByGraphMeetingId(sessionKey);
        TeamsMeeting m = existing.orElseGet(TeamsMeeting::new);
        boolean isNew = existing.isEmpty();

        m.setGraphMeetingId(sessionKey);
        m.setSubject(firstNonNull(
                strVal(s.get("topic")),
                strVal(s.get("subject")),
                strVal(s.get("title")),
                "Untitled meeting"));
        m.setDescription(firstNonNull(
                strVal(s.get("agenda")),
                strVal(s.get("description"))));
        m.setStartDateTime(startDt);
        m.setEndDateTime(endDt);

        String joinLink = firstNonNull(
                strVal(s.get("joinLink")),
                strVal(s.get("joinUrl")),
                strVal(s.get("meetingUrl")));
        if (joinLink != null) m.setJoinUrl(joinLink);

        String pw = firstNonNull(strVal(s.get("pwd")), strVal(s.get("password")));
        if (pw != null) m.setPassword(pw);

        m.setStatus(localStatus);
        m.setOrganizerUserId(orgId);
        m.setRecurring(isRecurring);
        if (isNew) {
            m.setCreatedBy("zoho-sync");
        }

        meetingRepository.save(m);
        return true;
    }

    // ─── PARTICIPANT FETCH — Meeting Participant Report API ────────────────

    @SuppressWarnings("unchecked")
    protected int fetchAndStoreParticipants(TeamsMeeting m) {
        String sessionKey = m.getGraphMeetingId();
        if (sessionKey == null) {
            log.warn("[ZohoSync] Meeting #{} has no sessionKey.", m.getId());
            return 0;
        }

        String url = meetingApiUrl + ATTENDEE_ENDPOINT;
        log.info("[ZohoSync] Fetching attendance for meeting #{} (key={}, subject='{}')",
                m.getId(), sessionKey, m.getSubject());
        log.info("[ZohoSync] URL: {}",
                url.replace("{orgId}", orgId).replace("{sessionKey}", sessionKey));

        List<Map<String, Object>> attendees;
        int participantsCount = 0;
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(zohoHeaders()), Map.class,
                    orgId, sessionKey);

            Map<String, Object> body = resp.getBody();
            log.info("[ZohoSync]   → HTTP {} keys={}",
                    resp.getStatusCode().value(),
                    body != null ? body.keySet() : "null");
            log.info("[ZohoSync]   → body: {}",
                    body != null ? truncate(body.toString(), 800) : "null");

            if (body != null && body.get("participantsCount") != null) {
                participantsCount = (int) toLong(body.get("participantsCount"), 0L);
                log.info("[ZohoSync]   → participantsCount={}", participantsCount);
            }

            attendees = extractAttendeeList(body);
            log.info("[ZohoSync]   → extracted {} participant(s) from response", attendees.size());

        } catch (HttpClientErrorException ex) {
            String raw = ex.getResponseBodyAsString();
            int status = ex.getStatusCode().value();
            log.warn("[ZohoSync]   → HTTP {} error: {}", status, truncate(raw, 400));

            if (raw.contains("INVALID_OAUTHTOKEN") || raw.contains("INVALID_SCOPE") ||
                    raw.contains("OAUTH_SCOPE_MISMATCH") || raw.contains("invalid_scope")) {
                log.error("[ZohoSync] *** OAUTH SCOPE ISSUE *** Token needs scope " +
                        "'ZohoMeeting.meeting.READ' (included in ZohoMeeting.meeting.ALL).");
            } else if (status == 404) {
                log.warn("[ZohoSync] 404 — meeting not found in Zoho or no participants recorded.");
            } else if (raw.contains("too many requests")) {
                log.warn("[ZohoSync] Rate limited by Zoho — retry on next scheduled run.");
            }
            m.setParticipantsSyncedAt(LocalDateTime.now());
            meetingRepository.save(m);
            return 0;
        } catch (HttpServerErrorException ex) {
            log.error("[ZohoSync]   → HTTP {} server error: {}",
                    ex.getStatusCode().value(), ex.getResponseBodyAsString());
            return 0;
        } catch (Exception ex) {
            log.error("[ZohoSync]   → unexpected error: {}", ex.getMessage());
            return 0;
        }

        if (attendees.isEmpty()) {
            log.info("[ZohoSync] No participants for session {} — nobody joined, " +
                    "or meeting hasn't ended yet.", sessionKey);
            m.setParticipantsSyncedAt(LocalDateTime.now());
            meetingRepository.save(m);
            return 0;
        }

        // Save participants
        participantRepository.deleteByMeetingId(m.getId());

        List<MeetingParticipant> saved = new ArrayList<>();
        for (Map<String, Object> a : attendees) {
            MeetingParticipant p = new MeetingParticipant();
            p.setMeetingId(m.getId());

            // Name: Zoho may provide 'name' or firstName/lastName
            String first = strVal(a.get("firstName"));
            String last  = strVal(a.get("lastName"));
            String full  = firstNonNull(
                    strVal(a.get("name")),
                    strVal(a.get("fullName")),
                    strVal(a.get("participantName")),
                    (first != null || last != null)
                            ? ((first != null ? first : "") + " "
                               + (last != null ? last : "")).trim()
                            : null);
            p.setName(full);

            // Email (may be missing for guest participants)
            String email = firstNonNull(
                    strVal(a.get("email")),
                    strVal(a.get("emailId")),
                    strVal(a.get("participantEmail")));
            p.setEmail(email != null ? email : "guest-" + System.nanoTime() + "@unknown");

            // Timestamps — Zoho returns epoch milliseconds
            Long joinMs = toLongObj(firstNonNullObj(
                    a.get("joinTime"), a.get("joinedTime"),
                    a.get("joinedTimeMillis"), a.get("joinTimeMillis")));
            Long leaveMs = toLongObj(firstNonNullObj(
                    a.get("leaveTime"), a.get("leftTime"),
                    a.get("leftTimeMillis"), a.get("leaveTimeMillis")));

            if (joinMs != null && joinMs > 0) {
                p.setJoinTime(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(joinMs), ZoneId.of("Asia/Kolkata")));
            }
            if (leaveMs != null && leaveMs > 0) {
                p.setLeaveTime(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(leaveMs), ZoneId.of("Asia/Kolkata")));
            }

            // Duration — Zoho returns milliseconds (verified from docs: 82790ms ≈ 82s)
            long durMs = toLong(firstNonNullObj(a.get("duration"), a.get("durationMs")), 0L);
            p.setDurationSeconds(durMs > 0 ? durMs / 1000 : computeDurationSeconds(p));

            saved.add(p);
        }

        if (!saved.isEmpty()) participantRepository.saveAll(saved);
        m.setParticipantsSyncedAt(LocalDateTime.now());
        meetingRepository.save(m);
        log.info("[ZohoSync] ✓ Stored {} participant(s) for meeting #{}",
                saved.size(), m.getId());
        return saved.size();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractAttendeeList(Map<String, Object> body) {
        if (body == null) return new ArrayList<>();
        // Zoho Meeting Participant Report response: {"participantsCount": N, "participants": [...]}
        for (String k : List.of(
                "participants", "participant",
                "attendees", "attendee",
                "data", "response", "report")) {
            Object v = body.get(k);
            if (v instanceof List<?> list && (!list.isEmpty() && list.get(0) instanceof Map)) {
                return (List<Map<String, Object>>) list;
            }
            if (v instanceof Map<?, ?> nested) {
                for (String ik : List.of("participants", "participant", "attendees", "attendee")) {
                    Object iv = ((Map<String, Object>) nested).get(ik);
                    if (iv instanceof List<?> list && (!list.isEmpty()
                            && list.get(0) instanceof Map)) {
                        return (List<Map<String, Object>>) list;
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private HttpHeaders zohoHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Zoho-oauthtoken " + tokenService.getAccessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String mapStatus(String zohoStatus) {
        if (zohoStatus == null) return "SCHEDULED";
        return zohoStatus.toLowerCase().contains("cancel") ? "CANCELLED" : "SCHEDULED";
    }

    private ParticipantDto toDto(MeetingParticipant p) {
        return ParticipantDto.builder()
                .id(p.getId())
                .name(p.getName())
                .email(p.getEmail())
                .joinTime(p.getJoinTime())
                .leaveTime(p.getLeaveTime())
                .durationSeconds(p.getDurationSeconds())
                .durationFormatted(formatDuration(p.getDurationSeconds()))
                .build();
    }

    private static String formatDuration(Long seconds) {
        if (seconds == null || seconds <= 0) return "—";
        long mins = seconds / 60;
        if (mins < 60) return mins + " min";
        long h = mins / 60, rem = mins % 60;
        return rem > 0 ? h + " hr " + rem + " min" : h + " hr";
    }

    private static long computeDurationSeconds(MeetingParticipant p) {
        if (p.getJoinTime() == null || p.getLeaveTime() == null) return 0L;
        return Duration.between(p.getJoinTime(), p.getLeaveTime()).getSeconds();
    }

    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static Object firstNonNullObj(Object... values) {
        for (Object v : values) if (v != null) return v;
        return null;
    }

    private static String strVal(Object v) { return strVal(v, null); }

    private static String strVal(Object v, String fallback) {
        if (v == null) return fallback;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? fallback : s;
    }

    private static long toLong(Object v, long fallback) {
        if (v instanceof Number n) return n.longValue();
        try { return v != null ? Long.parseLong(String.valueOf(v)) : fallback; }
        catch (NumberFormatException e) { return fallback; }
    }

    private static Long toLongObj(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); }
        catch (NumberFormatException e) { return null; }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...(" + (s.length() - max) + " more)";
    }

    private static LocalDateTime parseZohoDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        s = s.replaceAll("\\s+[A-Z]{2,5}$", "").trim();
        try { return LocalDateTime.parse(s, ZOHO_DATE_FMT); } catch (Exception ignore) {}
        try { return LocalDateTime.parse(s); } catch (Exception ignore) {}
        try { return OffsetDateTime.parse(s).toLocalDateTime(); } catch (Exception ignore) {}
        try {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(s)),
                    ZoneId.of("Asia/Kolkata"));
        } catch (NumberFormatException ignore) {}
        return null;
    }
}