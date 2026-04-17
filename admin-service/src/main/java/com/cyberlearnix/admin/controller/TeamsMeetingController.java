package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.dto.TeamsMeetingRequest;
import com.cyberlearnix.admin.dto.TeamsMeetingResponse;
import com.cyberlearnix.admin.service.TeamsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for managing Microsoft Teams meetings.
 *
 * All endpoints require ADMIN role (enforced by SecurityConfig).
 *
 * POST   /api/admin/teams/meetings             – schedule a new meeting
 * GET    /api/admin/teams/meetings             – list all meetings (optional ?status=SCHEDULED)
 * GET    /api/admin/teams/meetings/{id}        – get meeting details + join URL
 * DELETE /api/admin/teams/meetings/{id}        – cancel meeting
 */
@RestController
@RequestMapping("/api/admin/teams/meetings")
@RequiredArgsConstructor
public class TeamsMeetingController {

    private final TeamsService teamsService;

    @PostMapping
    public ResponseEntity<?> scheduleMeeting(@Valid @RequestBody TeamsMeetingRequest request) {
        try {
            String adminId = currentAdminId();
            TeamsMeetingResponse response = teamsService.scheduleMeeting(request, adminId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid request", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to schedule meeting", "message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<TeamsMeetingResponse>> getMeetings(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(teamsService.getMeetings(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMeeting(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(teamsService.getMeeting(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMeeting(@PathVariable Long id,
                                           @Valid @RequestBody TeamsMeetingRequest request) {
        try {
            return ResponseEntity.ok(teamsService.updateMeeting(id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid request", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid operation", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update meeting", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelMeeting(@PathVariable Long id) {
        try {
            teamsService.cancelMeeting(id);
            return ResponseEntity.ok(Map.of("message", "Meeting cancelled successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid operation", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to cancel meeting", "message", e.getMessage()));
        }
    }

    private String currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() != null)
                ? auth.getPrincipal().toString()
                : "unknown";
    }
}
