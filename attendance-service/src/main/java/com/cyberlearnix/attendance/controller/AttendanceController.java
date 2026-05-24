package com.cyberlearnix.attendance.controller;

import com.cyberlearnix.attendance.dto.*;
import com.cyberlearnix.attendance.entity.FinalAttendance;
import com.cyberlearnix.attendance.repository.CertificateEligibilityRepository;
import com.cyberlearnix.attendance.repository.FinalAttendanceRepository;
import com.cyberlearnix.attendance.repository.MeetingRepository;
import com.cyberlearnix.attendance.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Attendance", description = "Student attendance APIs")
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final FinalAttendanceRepository finalAttRepo;
    private final MeetingRepository meetingRepo;
    private final AttendanceAnalyticsService analyticsService;
    private final AttendanceEngineService engineService;
    private final CertificateEligibilityRepository certRepo;

    @Operation(summary = "Get my attendance for all meetings")
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TEACHER')")
    public ResponseEntity<Page<AttendanceDto>> getMyAttendance(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
            finalAttRepo.findByStudentId(userId, pageable).map(this::toDto)
        );
    }

    @Operation(summary = "Get my attendance summary report")
    @GetMapping("/my/report")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TEACHER')")
    public ResponseEntity<StudentAttendanceReport> getMyReport(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String courseId) {
        return ResponseEntity.ok(analyticsService.buildStudentReport(userId, courseId));
    }

    @Operation(summary = "Get attendance for a specific meeting")
    @GetMapping("/meeting/{meetingId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<List<AttendanceDto>> getMeetingAttendance(@PathVariable String meetingId) {
        List<FinalAttendance> records = finalAttRepo.findByMeetingIdOrderByStudentNameAsc(meetingId);
        return ResponseEntity.ok(records.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @Operation(summary = "Get my attendance for a specific meeting")
    @GetMapping("/meeting/{meetingId}/my")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TEACHER')")
    public ResponseEntity<AttendanceDto> getMyMeetingAttendance(
            @PathVariable String meetingId,
            @RequestHeader("X-User-Id") String userId) {
        return finalAttRepo.findByMeetingIdAndStudentId(meetingId, userId)
            .map(r -> ResponseEntity.ok(toDto(r)))
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get certificate eligibility for a course")
    @GetMapping("/certificate/eligibility")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN','TEACHER')")
    public ResponseEntity<?> getCertificateEligibility(
            @RequestParam String courseId,
            @RequestHeader("X-User-Id") String userId) {
        return certRepo.findByStudentIdAndCourseId(userId, courseId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Trigger attendance recalculation for a meeting")
    @PostMapping("/meeting/{meetingId}/recalculate")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<Map<String, String>> recalculate(@PathVariable String meetingId) {
        engineService.finalizeAttendanceForMeeting(meetingId);
        return ResponseEntity.ok(Map.of("status", "recalculation triggered", "meetingId", meetingId));
    }

    private AttendanceDto toDto(FinalAttendance fa) {
        AttendanceDto dto = new AttendanceDto();
        dto.setId(fa.getId());
        dto.setMeetingId(fa.getMeetingId());
        dto.setStudentId(fa.getStudentId());
        dto.setStudentName(fa.getStudentName());
        dto.setStudentEmail(fa.getStudentEmail());
        dto.setTotalActiveSeconds(fa.getTotalActiveSeconds());
        dto.setMeetingDurationSeconds(fa.getMeetingDurationSeconds());
        dto.setAttendancePercentage(fa.getAttendancePercentage());
        dto.setStatus(fa.getStatus());
        dto.setRejoinCount(fa.getRejoinCount());
        dto.setLate(fa.getLate());
        dto.setLateByMinutes(fa.getLateByMinutes());
        dto.setOverridden(fa.getOverridden());
        dto.setLocked(fa.getLocked());
        dto.setCountsForCertificate(fa.getCountsForCertificate());
        dto.setAdminNotes(fa.getAdminNotes());
        dto.setCreatedAt(fa.getCreatedAt());
        dto.setUpdatedAt(fa.getUpdatedAt());
        meetingRepo.findById(fa.getMeetingId()).ifPresent(m -> {
            dto.setMeetingTitle(m.getTitle());
            dto.setMeetingScheduledStart(m.getScheduledStart());
        });
        return dto;
    }
}
