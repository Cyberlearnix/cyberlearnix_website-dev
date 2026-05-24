package com.cyberlearnix.attendance.controller;

import com.cyberlearnix.attendance.dto.*;
import com.cyberlearnix.attendance.entity.*;
import com.cyberlearnix.attendance.repository.*;
import com.cyberlearnix.attendance.service.*;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Admin Attendance", description = "Admin attendance control APIs")
@RestController
@RequestMapping("/api/attendance/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminAttendanceController {

    private final FinalAttendanceRepository finalAttRepo;
    private final AttendanceOverrideRepository overrideRepo;
    private final AuditLogRepository auditRepo;
    private final AttendanceEngineService engineService;
    private final AttendanceAnalyticsService analyticsService;
    private final CertificateEligibilityRepository certRepo;
    private final MeetingRepository meetingRepo;

    @Operation(summary = "Get all attendance for a meeting")
    @GetMapping("/meetings/{meetingId}/attendance")
    public ResponseEntity<List<FinalAttendance>> getMeetingAttendance(@PathVariable String meetingId) {
        return ResponseEntity.ok(finalAttRepo.findByMeetingIdOrderByStudentNameAsc(meetingId));
    }

    @Operation(summary = "Get all attendance for a student")
    @GetMapping("/students/{studentId}/attendance")
    public ResponseEntity<List<FinalAttendance>> getStudentAttendance(@PathVariable String studentId) {
        return ResponseEntity.ok(finalAttRepo.findByStudentIdOrderByCreatedAtDesc(studentId));
    }

    @Operation(summary = "Override attendance for a student")
    @PostMapping("/override")
    public ResponseEntity<FinalAttendance> overrideAttendance(
            @Valid @RequestBody AttendanceOverrideRequest req,
            @RequestHeader("X-User-Id") String adminId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Admin") String adminName) {
        FinalAttendance result = engineService.applyAdminOverride(req, adminId, adminName);
        auditLog(adminId, adminName, "ATTENDANCE_OVERRIDE",
            "ATTENDANCE", result.getId(),
            "Override: " + req.getAction() + " for student " + req.getStudentId() + " in meeting " + req.getMeetingId());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Bulk override attendance for multiple students")
    @PostMapping("/override/bulk")
    public ResponseEntity<Map<String, String>> bulkOverride(
            @RequestBody List<AttendanceOverrideRequest> requests,
            @RequestHeader("X-User-Id") String adminId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Admin") String adminName) {
        int success = 0, failed = 0;
        for (AttendanceOverrideRequest req : requests) {
            try {
                engineService.applyAdminOverride(req, adminId, adminName);
                success++;
            } catch (Exception e) {
                failed++;
            }
        }
        return ResponseEntity.ok(Map.of("success", String.valueOf(success), "failed", String.valueOf(failed)));
    }

    @Operation(summary = "Lock attendance record")
    @PostMapping("/lock/{attendanceId}")
    public ResponseEntity<Map<String, String>> lock(
            @PathVariable String attendanceId,
            @RequestHeader("X-User-Id") String adminId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Admin") String adminName) {
        FinalAttendance fa = finalAttRepo.findById(attendanceId)
            .orElseThrow(() -> new IllegalArgumentException("Attendance not found"));
        fa.setLocked(true);
        finalAttRepo.save(fa);
        auditLog(adminId, adminName, "LOCK_ATTENDANCE", "ATTENDANCE", attendanceId, "Locked by admin");
        return ResponseEntity.ok(Map.of("status", "locked"));
    }

    @Operation(summary = "Unlock attendance record")
    @PostMapping("/unlock/{attendanceId}")
    public ResponseEntity<Map<String, String>> unlock(
            @PathVariable String attendanceId,
            @RequestHeader("X-User-Id") String adminId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Admin") String adminName) {
        FinalAttendance fa = finalAttRepo.findById(attendanceId)
            .orElseThrow(() -> new IllegalArgumentException("Attendance not found"));
        fa.setLocked(false);
        fa.setOverridden(false);
        finalAttRepo.save(fa);
        auditLog(adminId, adminName, "UNLOCK_ATTENDANCE", "ATTENDANCE", attendanceId, "Unlocked by admin");
        return ResponseEntity.ok(Map.of("status", "unlocked"));
    }

    @Operation(summary = "Finalize attendance for a meeting")
    @PostMapping("/meetings/{meetingId}/finalize")
    public ResponseEntity<Map<String, String>> finalize(
            @PathVariable String meetingId,
            @RequestHeader("X-User-Id") String adminId,
            @RequestHeader(value = "X-User-Name", defaultValue = "Admin") String adminName) {
        engineService.finalizeAttendanceForMeeting(meetingId);
        auditLog(adminId, adminName, "FINALIZE_ATTENDANCE", "MEETING", meetingId, "Manual finalization by admin");
        return ResponseEntity.ok(Map.of("status", "finalized", "meetingId", meetingId));
    }

    @Operation(summary = "Get attendance override history")
    @GetMapping("/overrides/meeting/{meetingId}")
    public ResponseEntity<List<AttendanceOverride>> getMeetingOverrides(@PathVariable String meetingId) {
        return ResponseEntity.ok(overrideRepo.findByMeetingIdOrderByCreatedAtDesc(meetingId));
    }

    @Operation(summary = "Get audit log")
    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLog>> getAuditLog(
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(auditRepo.findAllByOrderByCreatedAtDesc(pageable));
    }

    @Operation(summary = "Get course analytics")
    @GetMapping("/analytics/course/{courseId}")
    public ResponseEntity<AttendanceAnalyticsDto> getCourseAnalytics(@PathVariable String courseId) {
        return ResponseEntity.ok(analyticsService.buildCourseAnalytics(courseId));
    }

    @Operation(summary = "Get students at risk")
    @GetMapping("/analytics/at-risk")
    public ResponseEntity<List<Object[]>> getAtRiskStudents(
            @RequestParam(defaultValue = "60") double threshold) {
        return ResponseEntity.ok(finalAttRepo.findStudentsAtRisk(threshold));
    }

    @Operation(summary = "Get certificate eligibility list for a course")
    @GetMapping("/certificate/course/{courseId}")
    public ResponseEntity<List<CertificateEligibility>> getCertificateList(@PathVariable String courseId) {
        return ResponseEntity.ok(certRepo.findByCourseIdOrderByOverallAttendancePercentageDesc(courseId));
    }

    @Operation(summary = "Get all live meetings with participant counts")
    @GetMapping("/live/meetings")
    public ResponseEntity<List<Meeting>> getLiveMeetings() {
        return ResponseEntity.ok(meetingRepo.findLiveMeetings());
    }

    private void auditLog(String actorId, String actorName, String action, String entityType, String entityId, String desc) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setActorName(actorName);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(desc);
        auditRepo.save(log);
    }
}
