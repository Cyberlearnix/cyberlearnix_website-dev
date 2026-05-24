package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.entity.MeetingSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Broadcasts real-time attendance events over WebSocket (STOMP).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveAttendanceService {

    private static final String KEY_EVENT = "event";
    private static final String KEY_MEETING_ID = "meetingId";
    private static final String KEY_STUDENT_ID = "studentId";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String DEST_MEETING_PREFIX = "/topic/meeting/";
    private static final String DEST_SUFFIX_ATTENDANCE = "/attendance";
    private static final String DEST_ADMIN_LIVE = "/topic/admin/live";

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastMeetingStarted(String meetingId) {
        Map<String, Object> msg = Map.of(KEY_EVENT, "MEETING_STARTED", KEY_MEETING_ID, meetingId, KEY_TIMESTAMP, System.currentTimeMillis());
        send(DEST_MEETING_PREFIX + meetingId, msg);
    }

    public void broadcastMeetingEnded(String meetingId) {
        Map<String, Object> msg = Map.of(KEY_EVENT, "MEETING_ENDED", KEY_MEETING_ID, meetingId, KEY_TIMESTAMP, System.currentTimeMillis());
        send(DEST_MEETING_PREFIX + meetingId, msg);
        send(DEST_ADMIN_LIVE, msg);
    }

    public void broadcastParticipantJoined(String meetingId, MeetingSession session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(KEY_EVENT, "PARTICIPANT_JOINED");
        payload.put(KEY_MEETING_ID, meetingId);
        payload.put(KEY_STUDENT_ID, session.getStudentId());
        payload.put("studentName", session.getStudentName());
        payload.put("studentEmail", session.getStudentEmail());
        payload.put("joinedAt", session.getJoinedAt());
        payload.put("sessionSequence", session.getSessionSequence());
        payload.put(KEY_TIMESTAMP, System.currentTimeMillis());
        send(DEST_MEETING_PREFIX + meetingId + DEST_SUFFIX_ATTENDANCE, payload);
        send(DEST_ADMIN_LIVE, payload);
    }

    public void broadcastParticipantLeft(String meetingId, MeetingSession session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(KEY_EVENT, "PARTICIPANT_LEFT");
        payload.put(KEY_MEETING_ID, meetingId);
        payload.put(KEY_STUDENT_ID, session.getStudentId());
        payload.put("studentName", session.getStudentName());
        payload.put("leftAt", session.getLeftAt());
        payload.put("durationSeconds", session.getDurationSeconds());
        payload.put(KEY_TIMESTAMP, System.currentTimeMillis());
        send(DEST_MEETING_PREFIX + meetingId + DEST_SUFFIX_ATTENDANCE, payload);
        send(DEST_ADMIN_LIVE, payload);
    }

    public void broadcastAttendanceUpdate(String meetingId, String studentId, double percentage, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(KEY_EVENT, "ATTENDANCE_UPDATED");
        payload.put(KEY_MEETING_ID, meetingId);
        payload.put(KEY_STUDENT_ID, studentId);
        payload.put("attendancePercentage", percentage);
        payload.put("status", status);
        payload.put(KEY_TIMESTAMP, System.currentTimeMillis());
        send(DEST_MEETING_PREFIX + meetingId + DEST_SUFFIX_ATTENDANCE, payload);
        // Personal channel for student
        send("/topic/student/" + studentId + DEST_SUFFIX_ATTENDANCE, payload);
    }

    private void send(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.debug("WebSocket send failed for {}: {}", destination, e.getMessage());
        }
    }
}
