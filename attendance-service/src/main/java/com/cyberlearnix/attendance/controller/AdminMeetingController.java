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
@PreAuthorize("hasAnyRole('ADMIN', 'DUAL', 'INSTITUTE')")
public class AdminMeetingController {

    private final MeetingService meetingService;

    @PostMapping
    public ResponseEntity<MeetingResponse> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "admin") String adminId) {
        request.setFacultyId(adminId);
        MeetingResponse response = meetingService.createMeeting(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MeetingResponse>> getMeetings() {
        return ResponseEntity.ok(meetingService.getAllMeetings());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getMeetingById(@PathVariable String id) {
        return ResponseEntity.ok(meetingService.getMeetingById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MeetingResponse> updateMeeting(
            @PathVariable String id,
            @Valid @RequestBody CreateMeetingRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "admin") String adminId) {
        request.setFacultyId(adminId);
        return ResponseEntity.ok(meetingService.updateMeeting(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable String id) {
        meetingService.deleteMeeting(id);
        return ResponseEntity.noContent().build();
    }
}
