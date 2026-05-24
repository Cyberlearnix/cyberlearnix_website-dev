package com.cyberlearnix.attendance.controller;

import com.cyberlearnix.attendance.dto.CreateMeetingRequest;
import com.cyberlearnix.attendance.dto.MeetingDto;
import com.cyberlearnix.attendance.dto.LiveParticipantDto;
import com.cyberlearnix.attendance.entity.MeetingSession;
import com.cyberlearnix.attendance.repository.MeetingSessionRepository;
import com.cyberlearnix.attendance.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Meetings", description = "Meeting management APIs")
@RestController
@RequestMapping("/api/attendance/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingSessionRepository sessionRepo;

    @Operation(summary = "Create a new meeting")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','SUPER_ADMIN')")
    public ResponseEntity<MeetingDto> createMeeting(
            @Valid @RequestBody CreateMeetingRequest req,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Host") String userName) {
        return ResponseEntity.ok(meetingService.createMeeting(req, userId, userName));
    }

    @Operation(summary = "Get a meeting by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MeetingDto> getMeeting(@PathVariable String id) {
        return ResponseEntity.ok(meetingService.getMeeting(id));
    }

    @Operation(summary = "Get meetings for a course")
    @GetMapping("/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<MeetingDto>> getMeetingsByCourse(
            @PathVariable String courseId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(meetingService.getMeetingsByCourse(courseId, pageable));
    }

    @Operation(summary = "Get all live meetings")
    @GetMapping("/live")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MeetingDto>> getLiveMeetings() {
        return ResponseEntity.ok(meetingService.getLiveMeetings());
    }

    @Operation(summary = "Get upcoming meetings (next N hours)")
    @GetMapping("/upcoming")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MeetingDto>> getUpcomingMeetings(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(meetingService.getUpcomingMeetings(hours));
    }

    @Operation(summary = "Update a meeting")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','SUPER_ADMIN')")
    public ResponseEntity<MeetingDto> updateMeeting(
            @PathVariable String id,
            @RequestBody CreateMeetingRequest req) {
        return ResponseEntity.ok(meetingService.updateMeeting(id, req));
    }

    @Operation(summary = "Cancel a meeting")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> cancelMeeting(@PathVariable String id) {
        meetingService.cancelMeeting(id);
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    @Operation(summary = "Get live participants in a meeting")
    @GetMapping("/{id}/live-participants")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','SUPER_ADMIN')")
    public ResponseEntity<List<LiveParticipantDto>> getLiveParticipants(@PathVariable String id) {
        List<MeetingSession> activeSessions = sessionRepo.findActiveLiveParticipants(id);
        List<LiveParticipantDto> result = activeSessions.stream().map(s -> {
            LiveParticipantDto dto = new LiveParticipantDto();
            dto.setStudentId(s.getStudentId());
            dto.setStudentName(s.getStudentName());
            dto.setStudentEmail(s.getStudentEmail());
            dto.setJoinedAt(s.getJoinedAt());
            dto.setCurrentDurationSeconds(s.getJoinedAt() != null
                ? Duration.between(s.getJoinedAt(), LocalDateTime.now()).toSeconds() : 0L);
            long total = sessionRepo.countSessionsByMeetingAndStudent(id, s.getStudentId());
            dto.setRejoinCount((int) Math.max(0, total - 1));
            dto.setIpAddress(s.getIpAddress());
            dto.setDeviceInfo(s.getDeviceInfo());
            dto.setBrowserInfo(s.getBrowserInfo());
            dto.setStatus("ACTIVE");
            dto.setLastHeartbeat(s.getLastHeartbeat());
            return dto;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Heartbeat for a participant session")
    @PostMapping("/{meetingId}/heartbeat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> heartbeat(
            @PathVariable String meetingId,
            @RequestHeader("X-User-Id") String userId) {
        sessionRepo.findTopByMeetingIdAndStudentIdOrderByJoinedAtDesc(meetingId, userId)
            .ifPresent(s -> {
                if (s.getSessionStatus() == MeetingSession.SessionStatus.ACTIVE) {
                    s.setLastHeartbeat(LocalDateTime.now());
                    sessionRepo.save(s);
                }
            });
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
