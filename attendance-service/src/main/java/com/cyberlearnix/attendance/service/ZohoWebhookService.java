package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.dto.AttendanceOverrideRequest;
import com.cyberlearnix.attendance.entity.FinalAttendance;
import com.cyberlearnix.attendance.entity.Meeting;
import com.cyberlearnix.attendance.entity.MeetingSession;
import com.cyberlearnix.attendance.entity.AttendanceLog;
import com.cyberlearnix.attendance.dto.ZohoWebhookEvent;
import com.cyberlearnix.attendance.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Processes Zoho Meeting webhook events and drives session/attendance updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZohoWebhookService {

    private final MeetingRepository meetingRepository;
    private final MeetingSessionRepository sessionRepository;
    private final AttendanceLogRepository logRepository;
    private final FinalAttendanceRepository finalAttendanceRepository;
    private final AttendanceEngineService attendanceEngine;
    private final LiveAttendanceService liveAttendanceService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter ZOHO_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Transactional
    public void processEvent(ZohoWebhookEvent event, String rawPayload, String remoteIp) {
        if (event == null || event.getEvent() == null || event.getPayload() == null) {
            log.warn("Received null/empty Zoho event");
            return;
        }

        String eventType = event.getEvent();
        ZohoWebhookEvent.ZohoPayload payload = event.getPayload();

        // Find internal meeting by Zoho meeting ID
        Meeting meeting = meetingRepository.findByZohoMeetingId(payload.getMeetingId())
            .orElse(null);

        // Log the raw event regardless of whether we have a mapped meeting
        AttendanceLog attendanceLog = new AttendanceLog();
        attendanceLog.setMeetingId(meeting != null ? meeting.getId() : payload.getMeetingId());
        attendanceLog.setEventType(eventType);
        attendanceLog.setOccurredAt(LocalDateTime.now());
        attendanceLog.setRawPayload(rawPayload);
        attendanceLog.setIpAddress(remoteIp);

        if (payload.getParticipant() != null) {
            attendanceLog.setStudentEmail(payload.getParticipant().getEmail());
            attendanceLog.setZohoParticipantId(payload.getParticipant().getParticipantId());
            attendanceLog.setUserAgent(payload.getParticipant().getUserAgent());
        }
        logRepository.save(attendanceLog);

        if (meeting == null) {
            log.warn("No internal meeting found for Zoho meeting ID: {}. Event logged.", payload.getMeetingId());
            return;
        }

        switch (eventType) {
            case "meeting_started" -> handleMeetingStarted(meeting, payload);
            case "meeting_ended" -> handleMeetingEnded(meeting, payload);
            case "participant_joined" -> handleParticipantJoined(meeting, payload);
            case "participant_left" -> handleParticipantLeft(meeting, payload);
            case "participant_rejoined" -> handleParticipantRejoined(meeting, payload);
            default -> log.debug("Unhandled Zoho event type: {}", eventType);
        }
    }

    private void handleMeetingStarted(Meeting meeting, ZohoWebhookEvent.ZohoPayload payload) {
        log.info("Meeting started: {}", meeting.getId());
        meeting.setStatus(Meeting.MeetingStatus.LIVE);
        meeting.setActualStart(parseZohoTime(payload.getStartTime(), LocalDateTime.now()));
        meetingRepository.save(meeting);
        liveAttendanceService.broadcastMeetingStarted(meeting.getId());
    }

    private void handleMeetingEnded(Meeting meeting, ZohoWebhookEvent.ZohoPayload payload) {
        log.info("Meeting ended: {}", meeting.getId());
        LocalDateTime endTime = parseZohoTime(payload.getEndTime(), LocalDateTime.now());
        meeting.setStatus(Meeting.MeetingStatus.ENDED);
        meeting.setActualEnd(endTime);

        if (meeting.getActualStart() != null) {
            long minutes = java.time.Duration.between(meeting.getActualStart(), endTime).toMinutes();
            meeting.setDurationMinutes((int) minutes);
        }
        meetingRepository.save(meeting);

        // Close all still-active sessions
        sessionRepository.findByMeetingIdAndSessionStatus(meeting.getId(), MeetingSession.SessionStatus.ACTIVE)
            .forEach(s -> {
                s.setLeftAt(endTime);
                s.setSessionStatus(MeetingSession.SessionStatus.COMPLETED);
                if (s.getJoinedAt() != null) {
                    s.setDurationSeconds(java.time.Duration.between(s.getJoinedAt(), endTime).toSeconds());
                }
                sessionRepository.save(s);
            });

        // Finalize attendance asynchronously (within same TX will be picked up by scheduler too)
        attendanceEngine.finalizeAttendanceForMeeting(meeting.getId());
        liveAttendanceService.broadcastMeetingEnded(meeting.getId());
    }

    private void handleParticipantJoined(Meeting meeting, ZohoWebhookEvent.ZohoPayload payload) {
        ZohoWebhookEvent.ZohoParticipant participant = payload.getParticipant();
        if (participant == null) return;

        // Try to resolve student by email
        String email = participant.getEmail();
        log.info("Participant joined meeting {}: email={}", meeting.getId(), email);

        // Find if there's already an active session (duplicate join event guard)
        sessionRepository.findTopByMeetingIdAndStudentIdOrderByJoinedAtDesc(meeting.getId(), email)
            .ifPresent(existing -> {
                if (existing.getSessionStatus() == MeetingSession.SessionStatus.ACTIVE) {
                    log.debug("Duplicate join event for student {} — ignoring", email);
                    return;
                }
            });

        long sessionCount = sessionRepository.countSessionsByMeetingAndStudent(meeting.getId(), email);

        MeetingSession session = new MeetingSession();
        session.setMeetingId(meeting.getId());
        session.setStudentId(email); // using email as ID until user mapping resolves
        session.setStudentEmail(email);
        session.setStudentName(participant.getName());
        session.setJoinedAt(parseZohoTime(participant.getJoinTime(), LocalDateTime.now()));
        session.setSessionStatus(MeetingSession.SessionStatus.ACTIVE);
        session.setSessionSequence((int) sessionCount + 1);
        session.setZohoParticipantId(participant.getParticipantId());
        session.setIpAddress(participant.getIpAddress());
        session.setUserAgent(participant.getUserAgent());
        session.setDeviceInfo(participant.getDevice());
        session.setBrowserInfo(participant.getBrowser());
        session.setLastHeartbeat(LocalDateTime.now());
        sessionRepository.save(session);

        // Calculate incremental attendance (for live display)
        attendanceEngine.calculateAndSave(meeting.getId(), email);
        liveAttendanceService.broadcastParticipantJoined(meeting.getId(), session);
    }

    private void handleParticipantLeft(Meeting meeting, ZohoWebhookEvent.ZohoPayload payload) {
        ZohoWebhookEvent.ZohoParticipant participant = payload.getParticipant();
        if (participant == null) return;

        String email = participant.getEmail();
        log.info("Participant left meeting {}: email={}", meeting.getId(), email);

        LocalDateTime leaveTime = parseZohoTime(participant.getLeaveTime(), LocalDateTime.now());

        sessionRepository.findTopByMeetingIdAndStudentIdOrderByJoinedAtDesc(meeting.getId(), email)
            .ifPresent(session -> {
                if (session.getSessionStatus() == MeetingSession.SessionStatus.ACTIVE) {
                    session.setLeftAt(leaveTime);
                    session.setSessionStatus(MeetingSession.SessionStatus.COMPLETED);
                    long duration = participant.getDuration() != null
                        ? participant.getDuration()
                        : java.time.Duration.between(session.getJoinedAt(), leaveTime).toSeconds();
                    session.setDurationSeconds(duration);
                    sessionRepository.save(session);

                    attendanceEngine.calculateAndSave(meeting.getId(), email);
                    liveAttendanceService.broadcastParticipantLeft(meeting.getId(), session);
                }
            });
    }

    private void handleParticipantRejoined(Meeting meeting, ZohoWebhookEvent.ZohoPayload payload) {
        // Treat rejoin as a new join event
        handleParticipantJoined(meeting, payload);
    }

    private LocalDateTime parseZohoTime(String timeStr, LocalDateTime fallback) {
        if (timeStr == null || timeStr.isBlank()) return fallback;
        try {
            return LocalDateTime.parse(timeStr, ZOHO_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(timeStr);
            } catch (DateTimeParseException e2) {
                log.warn("Could not parse Zoho time: {}", timeStr);
                return fallback;
            }
        }
    }
}
