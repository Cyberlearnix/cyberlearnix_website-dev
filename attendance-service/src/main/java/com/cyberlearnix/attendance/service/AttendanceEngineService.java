package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.dto.AttendanceOverrideRequest;
import com.cyberlearnix.attendance.entity.*;
import com.cyberlearnix.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Core attendance calculation engine.
 * Computes final attendance status for a student in a given meeting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceEngineService {

    private final MeetingRepository meetingRepository;
    private final MeetingSessionRepository sessionRepository;
    private final FinalAttendanceRepository finalAttendanceRepository;
    private final AttendanceOverrideRepository overrideRepository;
    private final AuditLogRepository auditLogRepository;
    private final CertificateEligibilityService certService;

    @Lazy
    @Autowired
    private AttendanceEngineService self;

    @Value("${attendance.min-present-percent:80}")
    private double minPresentPercent;

    @Value("${attendance.min-partial-percent:40}")
    private double minPartialPercent;

    @Value("${attendance.min-late-join-minutes:10}")
    private int lateJoinThresholdMinutes;

    @Value("${attendance.certificate-min-percent:80}")
    private double certificateMinPercent;

    /**
     * Recalculate and persist the FinalAttendance record for one student in one meeting.
     * Called after meeting ends, or when admin triggers recalculation.
     */
    @Transactional
    public FinalAttendance calculateAndSave(String meetingId, String studentId) {
        Meeting meeting = meetingRepository.findById(meetingId)
            .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));

        List<MeetingSession> sessions = sessionRepository
            .findByMeetingIdAndStudentIdOrderByJoinedAtAsc(meetingId, studentId);

        // Meeting effective duration in seconds
        long meetingDurationSeconds = computeMeetingDuration(meeting);

        // Sum only completed sessions
        long totalActiveSeconds = sessions.stream()
            .filter(s -> s.getSessionStatus() == MeetingSession.SessionStatus.COMPLETED
                      || s.getLeftAt() != null)
            .mapToLong(s -> {
                if (s.getDurationSeconds() != null) return s.getDurationSeconds();
                if (s.getLeftAt() != null) {
                    return Duration.between(s.getJoinedAt(), s.getLeftAt()).toSeconds();
                }
                return 0L;
            })
            .sum();

        double percentage = meetingDurationSeconds > 0
            ? Math.min(100.0, (double) totalActiveSeconds / meetingDurationSeconds * 100.0)
            : 0.0;

        // Determine first join time for late detection
        LocalDateTime firstJoin = sessions.stream()
            .map(MeetingSession::getJoinedAt)
            .min(LocalDateTime::compareTo)
            .orElse(null);

        boolean late = false;
        int lateByMinutes = 0;
        if (firstJoin != null && meeting.getScheduledStart() != null) {
            long minutesAfterStart = Duration.between(meeting.getScheduledStart(), firstJoin).toMinutes();
            if (minutesAfterStart > lateJoinThresholdMinutes) {
                late = true;
                lateByMinutes = (int) minutesAfterStart;
            }
        }

        int rejoinCount = Math.max(0, sessions.size() - 1);

        FinalAttendance.AttendanceStatus status = determineStatus(percentage, late);

        // Get or create the record
        FinalAttendance fa = finalAttendanceRepository
            .findByMeetingIdAndStudentId(meetingId, studentId)
            .orElseGet(() -> {
                FinalAttendance newFa = new FinalAttendance();
                newFa.setMeetingId(meetingId);
                newFa.setStudentId(studentId);
                // Populate name/email from first session if available
                sessions.stream().findFirst().ifPresent(s -> {
                    newFa.setStudentName(s.getStudentName());
                    newFa.setStudentEmail(s.getStudentEmail());
                });
                return newFa;
            });

        // Do not overwrite locked records unless explicitly unlocked
        if (Boolean.TRUE.equals(fa.getLocked()) && !Boolean.TRUE.equals(fa.getOverridden())) {
            log.debug("Skipping recalculation for locked attendance: meeting={}, student={}", meetingId, studentId);
            return fa;
        }

        // Only update if not overridden by admin
        if (!Boolean.TRUE.equals(fa.getOverridden())) {
            fa.setTotalActiveSeconds(totalActiveSeconds);
            fa.setMeetingDurationSeconds(meetingDurationSeconds);
            fa.setAttendancePercentage(percentage);
            fa.setStatus(status);
            fa.setRejoinCount(rejoinCount);
            fa.setLate(late);
            fa.setLateByMinutes(lateByMinutes);
            fa.setCountsForCertificate(percentage >= certificateMinPercent);
        }

        fa = finalAttendanceRepository.save(fa);
        log.info("Attendance calculated: meeting={}, student={}, %={}%, status={}", meetingId, studentId, String.format("%.1f", percentage), status);
        return fa;
    }

    /**
     * Finalize attendance for ALL students in a meeting after it ends.
     */
    @Transactional
    public void finalizeAttendanceForMeeting(String meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
            .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));

        if (Boolean.TRUE.equals(meeting.getAttendanceFinalized())) {
            log.info("Attendance already finalized for meeting {}", meetingId);
            return;
        }

        List<String> participantIds = sessionRepository.findDistinctStudentIdsByMeetingId(meetingId);
        log.info("Finalizing attendance for {} participants in meeting {}", participantIds.size(), meetingId);

        for (String studentId : participantIds) {
            try {
                self.calculateAndSave(meetingId, studentId);
            } catch (Exception e) {
                log.error("Error calculating attendance for student {} in meeting {}", studentId, meetingId, e);
            }
        }

        meeting.setAttendanceFinalized(true);
        meetingRepository.save(meeting);

        // Trigger certificate eligibility recalculation
        if (meeting.getCourseId() != null) {
            certService.recalculateForCourse(meeting.getCourseId());
        }

        log.info("Attendance finalized for meeting {}", meetingId);
    }

    /**
     * Admin override: force-set status and optionally override duration.
     */
    @Transactional
    public FinalAttendance applyAdminOverride(AttendanceOverrideRequest request, String adminId, String adminName) {
        FinalAttendance fa = finalAttendanceRepository
            .findByMeetingIdAndStudentId(request.getMeetingId(), request.getStudentId())
            .orElseGet(() -> {
                FinalAttendance newFa = new FinalAttendance();
                newFa.setMeetingId(request.getMeetingId());
                newFa.setStudentId(request.getStudentId());
                return newFa;
            });

        // Save current values for audit
        AttendanceOverride override = new AttendanceOverride();
        override.setFinalAttendanceId(fa.getId());
        override.setMeetingId(request.getMeetingId());
        override.setStudentId(request.getStudentId());
        override.setAdminId(adminId);
        override.setAdminName(adminName);
        override.setPreviousStatus(fa.getStatus());
        override.setPreviousPercentage(fa.getAttendancePercentage());
        override.setPreviousActiveSeconds(fa.getTotalActiveSeconds());
        override.setAction(request.getAction());
        override.setReason(request.getReason());
        override.setNotes(request.getNotes());

        String action = request.getAction();
        switch (action) {
            case "MARK_PRESENT" -> {
                fa.setStatus(FinalAttendance.AttendanceStatus.PRESENT);
                fa.setAttendancePercentage(100.0);
                fa.setCountsForCertificate(true);
            }
            case "MARK_ABSENT" -> {
                fa.setStatus(FinalAttendance.AttendanceStatus.ABSENT);
                fa.setCountsForCertificate(false);
            }
            case "MARK_EXCUSED" -> {
                fa.setStatus(FinalAttendance.AttendanceStatus.EXCUSED);
                fa.setCountsForCertificate(true);
            }
            case "MARK_PARTIAL" -> fa.setStatus(FinalAttendance.AttendanceStatus.PARTIAL);
            case "EDIT_DURATION" -> {
                if (request.getNewActiveSeconds() != null) {
                    fa.setTotalActiveSeconds(request.getNewActiveSeconds());
                    double newPct = fa.getMeetingDurationSeconds() != null && fa.getMeetingDurationSeconds() > 0
                        ? Math.min(100.0, (double) request.getNewActiveSeconds() / fa.getMeetingDurationSeconds() * 100.0)
                        : 0.0;
                    fa.setAttendancePercentage(newPct);
                    fa.setStatus(determineStatus(newPct, fa.getLate()));
                    fa.setCountsForCertificate(newPct >= certificateMinPercent);
                }
                if (request.getNewPercentage() != null) {
                    fa.setAttendancePercentage(request.getNewPercentage());
                    fa.setStatus(determineStatus(request.getNewPercentage(), fa.getLate()));
                    fa.setCountsForCertificate(request.getNewPercentage() >= certificateMinPercent);
                }
            }
            case "LOCK" -> fa.setLocked(true);
            case "UNLOCK" -> {
                fa.setLocked(false);
                fa.setOverridden(false);
            }
            default -> throw new IllegalArgumentException("Unknown override action: " + action);
        }

        if (request.getNewStatus() != null) {
            fa.setStatus(request.getNewStatus());
        }
        if (request.getNotes() != null) {
            fa.setAdminNotes(request.getNotes());
        }

        if (!"LOCK".equals(action) && !"UNLOCK".equals(action)) {
            fa.setOverridden(true);
            fa.setOverrideBy(adminId);
            fa.setOverrideAt(LocalDateTime.now());
            fa.setOverrideReason(request.getReason());
        }

        override.setNewStatus(fa.getStatus());
        override.setNewPercentage(fa.getAttendancePercentage());
        override.setNewActiveSeconds(fa.getTotalActiveSeconds());

        finalAttendanceRepository.save(fa);
        overrideRepository.save(override);
        return fa;
    }

    // ---- helpers ----

    private long computeMeetingDuration(Meeting meeting) {
        if (meeting.getActualStart() != null && meeting.getActualEnd() != null) {
            return Duration.between(meeting.getActualStart(), meeting.getActualEnd()).toSeconds();
        }
        if (meeting.getScheduledStart() != null && meeting.getScheduledEnd() != null) {
            return Duration.between(meeting.getScheduledStart(), meeting.getScheduledEnd()).toSeconds();
        }
        if (meeting.getDurationMinutes() != null) {
            return meeting.getDurationMinutes() * 60L;
        }
        return 3600L; // default 1 hour
    }

    private FinalAttendance.AttendanceStatus determineStatus(double percentage, boolean late) {
        if (percentage <= 0) return FinalAttendance.AttendanceStatus.ABSENT;
        if (percentage >= minPresentPercent) {
            return late ? FinalAttendance.AttendanceStatus.LATE : FinalAttendance.AttendanceStatus.PRESENT;
        }
        if (percentage >= minPartialPercent) return FinalAttendance.AttendanceStatus.PARTIAL;
        return FinalAttendance.AttendanceStatus.ABSENT;
    }
}
