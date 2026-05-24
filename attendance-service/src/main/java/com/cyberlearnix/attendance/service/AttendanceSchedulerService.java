package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.entity.*;
import com.cyberlearnix.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Background scheduler tasks for attendance management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceSchedulerService {

    private final MeetingRepository meetingRepo;
    private final MeetingSessionRepository sessionRepo;
    private final AttendanceEngineService attendanceEngine;

    /**
     * Every 5 minutes: finalize attendance for meetings that ended but weren't finalized.
     */
    @Scheduled(fixedDelay = 300_000)
    public void finalizeEndedMeetings() {
        List<Meeting> meetings = meetingRepo.findMeetingsNeedingFinalization();
        for (Meeting meeting : meetings) {
            try {
                log.info("Scheduler: Finalizing attendance for meeting {}", meeting.getId());
                attendanceEngine.finalizeAttendanceForMeeting(meeting.getId());
            } catch (Exception e) {
                log.error("Scheduler: Error finalizing meeting {}", meeting.getId(), e);
            }
        }
    }

    /**
     * Every 2 minutes: detect stale active sessions (participant disconnected without webhook).
     * Marks them DISCONNECTED if last heartbeat is older than 5 minutes.
     */
    @Scheduled(fixedDelay = 120_000)
    public void detectDisconnectedParticipants() {
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(5);
        List<MeetingSession> staleSessions = sessionRepo.findStaleActiveSessions(staleThreshold);
        for (MeetingSession session : staleSessions) {
            try {
                session.setSessionStatus(MeetingSession.SessionStatus.DISCONNECTED);
                if (session.getJoinedAt() != null) {
                    long duration = java.time.Duration.between(session.getJoinedAt(),
                        session.getLastHeartbeat() != null ? session.getLastHeartbeat() : LocalDateTime.now()).toSeconds();
                    session.setDurationSeconds(duration);
                    session.setLeftAt(session.getLastHeartbeat() != null ? session.getLastHeartbeat() : LocalDateTime.now());
                }
                sessionRepo.save(session);
                log.debug("Marked session {} as DISCONNECTED", session.getId());
            } catch (Exception e) {
                log.error("Error marking stale session {}", session.getId(), e);
            }
        }
    }

    /**
     * Every hour: auto-mark meetings as ENDED if scheduledEnd passed but still LIVE/SCHEDULED.
     */
    @Scheduled(fixedDelay = 3_600_000)
    public void autoEndStaleMeetings() {
        LocalDateTime now = LocalDateTime.now();
        List<Meeting> liveMeetings = meetingRepo.findByStatus(Meeting.MeetingStatus.LIVE);
        liveMeetings.stream()
            .filter(m -> m.getScheduledEnd() != null && m.getScheduledEnd().plusHours(2).isBefore(now))
            .forEach(m -> {
                log.warn("Auto-ending stale meeting: {}", m.getId());
                m.setStatus(Meeting.MeetingStatus.ENDED);
                m.setActualEnd(m.getScheduledEnd());
                meetingRepo.save(m);
                try {
                    attendanceEngine.finalizeAttendanceForMeeting(m.getId());
                } catch (Exception e) {
                    log.error("Failed to auto-finalize meeting {}", m.getId(), e);
                }
            });
    }
}
