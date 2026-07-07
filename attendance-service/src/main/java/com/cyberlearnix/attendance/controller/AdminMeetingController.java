package com.cyberlearnix.attendance.controller;

import com.cyberlearnix.attendance.dto.CreateMeetingRequest;
import com.cyberlearnix.attendance.dto.MeetingResponse;
import com.cyberlearnix.attendance.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/meetings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMeetingController {

    private final MeetingService meetingService;

    @PostMapping
    public ResponseEntity<MeetingResponse> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "admin") String adminId) {
        MeetingResponse response = meetingService.createMeeting(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MeetingResponse>> getMeetings() {
        return ResponseEntity.ok(meetingService.getAllMeetings());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MeetingResponse> updateMeeting(
            @PathVariable String id,
            @Valid @RequestBody CreateMeetingRequest request) {
        return ResponseEntity.ok(meetingService.updateMeeting(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable String id) {
        meetingService.deleteMeeting(id);
        return ResponseEntity.noContent().build();
    }
}
