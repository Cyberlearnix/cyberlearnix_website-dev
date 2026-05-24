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

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastMeetingStarted(String meetingId) {
        Map<String, Object> msg = Map.of("event", "MEETING_STARTED", "meetingId", meetingId, "timestamp", System.currentTimeMillis());
        send("/topic/meeting/" + meetingId, msg);
    }

    public void broadcastMeetingEnded(String meetingId) {
        Map<String, Object> msg = Map.of("event", "MEETING_ENDED", "meetingId", meetingId, "timestamp", System.currentTimeMillis());
        send("/topic/meeting/" + meetingId, msg);
        send("/topic/admin/live", msg);
    }

    public void broadcastParticipantJoined(String meetingId, MeetingSession session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "PARTICIPANT_JOINED");
        payload.put("meetingId", meetingId);
        payload.put("studentId", session.getStudentId());
        payload.put("studentName", session.getStudentName());
        payload.put("studentEmail", session.getStudentEmail());
        payload.put("joinedAt", session.getJoinedAt());
        payload.put("sessionSequence", session.getSessionSequence());
        payload.put("timestamp", System.currentTimeMillis());
        send("/topic/meeting/" + meetingId + "/attendance", payload);
        send("/topic/admin/live", payload);
    }

    public void broadcastParticipantLeft(String meetingId, MeetingSession session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "PARTICIPANT_LEFT");
        payload.put("meetingId", meetingId);
        payload.put("studentId", session.getStudentId());
        payload.put("studentName", session.getStudentName());
        payload.put("leftAt", session.getLeftAt());
        payload.put("durationSeconds", session.getDurationSeconds());
        payload.put("timestamp", System.currentTimeMillis());
        send("/topic/meeting/" + meetingId + "/attendance", payload);
        send("/topic/admin/live", payload);
    }

    public void broadcastAttendanceUpdate(String meetingId, String studentId, double percentage, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "ATTENDANCE_UPDATED");
        payload.put("meetingId", meetingId);
        payload.put("studentId", studentId);
        payload.put("attendancePercentage", percentage);
        payload.put("status", status);
        payload.put("timestamp", System.currentTimeMillis());
        send("/topic/meeting/" + meetingId + "/attendance", payload);
        // Personal channel for student
        send("/topic/student/" + studentId + "/attendance", payload);
    }

    private void send(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.debug("WebSocket send failed for {}: {}", destination, e.getMessage());
        }
    }
}
