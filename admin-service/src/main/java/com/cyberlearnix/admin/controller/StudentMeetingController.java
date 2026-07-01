package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.dto.TeamsMeetingResponse;
import com.cyberlearnix.admin.service.TeamsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class StudentMeetingController {

    private final TeamsService teamsService;

    /**
     * GET /api/meetings/course/{courseId}
     * Returns non-cancelled meetings for a specific course.
     * Accessible by: any authenticated user.
     */
    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<TeamsMeetingResponse>> getMeetingsByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(teamsService.getMeetingsByCourseId(courseId));
    }

    /**
     * GET /api/meetings/by-courses?courseIds=1,2,3
     * Returns non-cancelled meetings for multiple courses (student dashboard bulk fetch).
     * Accessible by: any authenticated user.
     */
    @GetMapping("/by-courses")
    public ResponseEntity<List<TeamsMeetingResponse>> getMeetingsByCourses(
            @RequestParam List<Long> courseIds) {
        return ResponseEntity.ok(teamsService.getMeetingsByCourseIds(courseIds));
    }

    /**
     * GET /api/meetings/upcoming
     * Returns all upcoming and in-progress non-cancelled meetings.
     * Useful for students who want to see all available sessions.
     * Accessible by: any authenticated user.
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<TeamsMeetingResponse>> getUpcomingMeetings() {
        return ResponseEntity.ok(teamsService.getUpcomingMeetings());
    }
}
