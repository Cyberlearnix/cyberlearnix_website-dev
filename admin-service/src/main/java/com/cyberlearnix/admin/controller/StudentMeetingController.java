package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.dto.MeetingResponse;
import com.cyberlearnix.admin.entity.Meeting;
import com.cyberlearnix.admin.entity.MeetingAttendance;
import com.cyberlearnix.admin.repository.MeetingAttendanceRepository;
import com.cyberlearnix.admin.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Student-facing meeting endpoints served from admin-service.
 * Authenticated by student JWT (ROLE_STUDENT is allowed via SecurityConfig).
 * These mirror the same paths the student portal has always called so no
 * frontend change is needed for routing.
 */
@Slf4j
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class StudentMeetingController {

    private final MeetingRepository meetingRepository;
    private final MeetingAttendanceRepository attendanceRepository;

    /**
     * GET /api/meetings/by-courses?courseIds=1,2,3
     * Returns meetings for the student's enrolled courses.
     */
    @GetMapping("/by-courses")
    public ResponseEntity<List<MeetingResponse>> byCourses(
            @RequestParam List<Long> courseIds) {
        List<Meeting> meetings = meetingRepository.findByCourseIdIn(courseIds);
        meetings.sort(Comparator.comparing(Meeting::getStartTime).reversed());
        return ResponseEntity.ok(meetings.stream().map(this::toResponse).toList());
    }

    /**
     * GET /api/meetings/upcoming
     * Returns meetings starting within the next 24 hours.
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<MeetingResponse>> upcoming() {
        LocalDateTime now = LocalDateTime.now();
        List<Meeting> meetings = meetingRepository.findUpcomingMeetings(now, now.plusHours(24));
        return ResponseEntity.ok(meetings.stream().map(this::toResponse).toList());
    }

    /**
     * GET /api/meetings/course/{courseId}
     * Returns all meetings for a specific course.
     */
    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<MeetingResponse>> byCourse(@PathVariable Long courseId) {
        List<Meeting> meetings = meetingRepository.findByCourseId(courseId);
        meetings.sort(Comparator.comparing(Meeting::getStartTime).reversed());
        return ResponseEntity.ok(meetings.stream().map(this::toResponse).toList());
    }

    // ── Attendance ──────────────────────────────────────────────────────────

    /**
     * POST /api/meetings/{id}/attendance
     * Records that a student joined. Called by the student portal before opening Jitsi.
     */
    @PostMapping("/{id}/attendance")
    public ResponseEntity<Map<String, Object>> recordJoin(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String studentId) {

        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Meeting not found: " + id));

        if (studentId != null && !studentId.isBlank()) {
            attendanceRepository.findByMeetingIdAndStudentId(id, studentId)
                    .ifPresentOrElse(
                            existing -> log.info("[Attendance] Re-join by {} for meeting {}", studentId, id),
                            () -> {
                                MeetingAttendance a = new MeetingAttendance();
                                a.setMeetingId(id);
                                a.setStudentId(studentId);
                                a.setJoinTime(LocalDateTime.now());
                                a.setStatus("PRESENT");
                                attendanceRepository.save(a);
                                log.info("[Attendance] Recorded join: student={} meeting={}", studentId, id);
                            });
        }

        String jitsiUrl = meeting.getJoinUrl() != null
                ? meeting.getJoinUrl() : "https://meet.jit.si/" + meeting.getMeetingCode();

        return ResponseEntity.ok(Map.of(
                "joinUrl", jitsiUrl,
                "meetingCode", meeting.getMeetingCode() != null ? meeting.getMeetingCode() : "",
                "title", meeting.getTitle() != null ? meeting.getTitle() : ""
        ));
    }

    /**
     * PUT /api/meetings/{id}/attendance/leave
     * Records leave time. Called by the student portal's beforeunload event.
     */
    @PutMapping("/{id}/attendance/leave")
    public ResponseEntity<Void> recordLeave(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String studentId) {

        if (studentId == null || studentId.isBlank()) return ResponseEntity.ok().build();

        attendanceRepository.findByMeetingIdAndStudentId(id, studentId).ifPresent(a -> {
            if (a.getLeaveTime() == null) {
                a.setLeaveTime(LocalDateTime.now());
                if (a.getJoinTime() != null) {
                    long mins = Duration.between(a.getJoinTime(), a.getLeaveTime()).toMinutes();
                    a.setDurationMinutes((int) Math.max(0, mins));
                }
                attendanceRepository.save(a);
                log.info("[Attendance] Recorded leave: student={} meeting={} duration={}m",
                        studentId, id, a.getDurationMinutes());
            }
        });
        return ResponseEntity.ok().build();
    }

    private MeetingResponse toResponse(Meeting m) {
        MeetingResponse r = new MeetingResponse();
        r.setId(m.getId());
        r.setTitle(m.getTitle());
        r.setDescription(m.getDescription());
        r.setMeetingCode(m.getMeetingCode());
        r.setCourseId(m.getCourseId());
        r.setFacultyId(m.getFacultyId());
        r.setStartTime(m.getStartTime());
        r.setEndTime(m.getEndTime());
        r.setStatus(m.getStatus() != null ? m.getStatus().name() : "SCHEDULED");
        r.setJoinUrl(m.getJoinUrl() != null ? m.getJoinUrl()
                : "https://meet.jit.si/" + m.getMeetingCode());
        r.setCreatedBy(m.getCreatedBy());
        r.setCreatedAt(m.getCreatedAt());
        r.setUpdatedAt(m.getUpdatedAt());
        return r;
    }
}
