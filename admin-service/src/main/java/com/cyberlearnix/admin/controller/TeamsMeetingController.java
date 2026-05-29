package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.dto.ParticipantDto;
import com.cyberlearnix.admin.dto.TeamsMeetingRequest;
import com.cyberlearnix.admin.dto.TeamsMeetingResponse;
import com.cyberlearnix.admin.service.TeamsService;
import com.cyberlearnix.admin.service.ZohoSyncService;
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
 * Admin endpoints for managing Zoho meetings.
 *
 * POST   /api/admin/teams/meetings                         – schedule
 * GET    /api/admin/teams/meetings                         – list
 * GET    /api/admin/teams/meetings/{id}                    – details
 * PUT    /api/admin/teams/meetings/{id}                    – reschedule / update invitees
 * DELETE /api/admin/teams/meetings/{id}                    – cancel
 *
 * GET    /api/admin/teams/meetings/{id}/participants       – attendance from local DB
 *         (?sync=true to force a fresh Zoho fetch first)
 * POST   /api/admin/teams/meetings/{id}/sync-participants  – force fetch participants from Zoho
 * POST   /api/admin/teams/meetings/sync                    – force pull all sessions from Zoho
 *
 * GET    /api/admin/teams/meetings/debug-zoho?type=upcoming – DEBUG: raw Zoho response
 */
@RestController
@RequestMapping("/api/admin/teams/meetings")
@RequiredArgsConstructor
public class TeamsMeetingController {

    private final TeamsService teamsService;
    private final ZohoSyncService zohoSyncService;

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

    // ─── Attendance / sync endpoints ────────────────────────────────────────

    @GetMapping("/{id}/participants")
    public ResponseEntity<?> getParticipants(@PathVariable Long id,
                                             @RequestParam(defaultValue = "false") boolean sync) {
        try {
            if (sync) {
                zohoSyncService.triggerParticipantSync(id);
            }
            List<ParticipantDto> participants = zohoSyncService.getParticipants(id);
            return ResponseEntity.ok(participants);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch participants", "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/sync-participants")
    public ResponseEntity<?> syncParticipants(@PathVariable Long id) {
        try {
            int count = zohoSyncService.triggerParticipantSync(id);
            return ResponseEntity.ok(Map.of("synced", count));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to sync participants", "message", e.getMessage()));
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncAllMeetings() {
        try {
            int n = zohoSyncService.triggerMeetingSync();
            return ResponseEntity.ok(Map.of("upserted", n));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to sync meetings", "message", e.getMessage()));
        }
    }

    /**
     * DEBUG-ONLY. Returns Zoho's raw response so we can see exactly what the
     * API returns for this org and fix the parser accordingly.
     *
     * Example: GET /api/admin/teams/meetings/debug-zoho?type=upcoming
     *          GET /api/admin/teams/meetings/debug-zoho?type=past
     */
    @GetMapping("/debug-zoho")
    public ResponseEntity<?> debugZoho(@RequestParam(defaultValue = "upcoming") String type) {
        try {
            Map<String, Object> raw = zohoSyncService.debugFetchZohoRaw(type);
            return ResponseEntity.ok(raw);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private String currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() != null)
                ? auth.getPrincipal().toString()
                : "unknown";
    }
}