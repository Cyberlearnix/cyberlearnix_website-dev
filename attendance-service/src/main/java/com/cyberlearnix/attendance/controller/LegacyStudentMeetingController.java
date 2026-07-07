package com.cyberlearnix.attendance.controller;

import com.cyberlearnix.attendance.dto.MeetingResponse;
import com.cyberlearnix.attendance.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class LegacyStudentMeetingController {

    private final MeetingService meetingService;

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<MeetingResponse>> getMeetingsByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(meetingService.getMeetingsByCourse(courseId));
    }

    @GetMapping("/by-courses")
    public ResponseEntity<List<MeetingResponse>> getMeetingsByCourses(
            @RequestParam List<Long> courseIds) {
        return ResponseEntity.ok(meetingService.getMeetingsByCourses(courseIds));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<MeetingResponse>> getUpcomingMeetings() {
        return ResponseEntity.ok(meetingService.getUpcomingMeetings());
    }
}
