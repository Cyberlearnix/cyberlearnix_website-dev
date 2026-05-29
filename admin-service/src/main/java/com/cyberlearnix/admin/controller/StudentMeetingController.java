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
     * Returns upcoming meetings for a specific course.
     * Accessible by: any logged-in user (student).
     */
    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<TeamsMeetingResponse>> getMeetingsByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(teamsService.getMeetingsByCourseId(courseId));
    }
}
