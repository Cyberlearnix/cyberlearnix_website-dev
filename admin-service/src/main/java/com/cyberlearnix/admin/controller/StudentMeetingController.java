package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.dto.MeetingResponse;
import com.cyberlearnix.admin.entity.Meeting;
import com.cyberlearnix.admin.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Student-facing meeting endpoints served from admin-service.
 * Authenticated by student JWT (ROLE_STUDENT is allowed via SecurityConfig).
 * These mirror the same paths the student portal has always called so no
 * frontend change is needed for routing.
 */
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class StudentMeetingController {

    private final MeetingRepository meetingRepository;

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
