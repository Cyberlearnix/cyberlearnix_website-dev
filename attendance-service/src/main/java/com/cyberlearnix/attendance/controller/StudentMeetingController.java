package com.cyberlearnix.attendance.controller;

import com.cyberlearnix.attendance.dto.MeetingResponse;
import com.cyberlearnix.attendance.service.MeetingAuthorizationService;
import com.cyberlearnix.attendance.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student/meetings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentMeetingController {

    private final MeetingService meetingService;
    private final MeetingAuthorizationService authService;

    @GetMapping
    public ResponseEntity<List<MeetingResponse>> getMeetings() {
        return ResponseEntity.ok(meetingService.getAllMeetings());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getMeeting(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        authService.authorizeStudentJoin(id, userId);
        return ResponseEntity.ok(meetingService.getMeetingById(id));
    }
}
