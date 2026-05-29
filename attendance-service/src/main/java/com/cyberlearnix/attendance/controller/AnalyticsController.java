package com.cyberlearnix.attendance.controller;

import com.cyberlearnix.attendance.dto.AttendanceAnalyticsDto;
import com.cyberlearnix.attendance.dto.StudentAttendanceReport;
import com.cyberlearnix.attendance.service.AttendanceAnalyticsService;
import com.cyberlearnix.attendance.service.AttendanceExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "Analytics & Reports", description = "Attendance analytics and export APIs")
@RestController
@RequestMapping("/api/attendance/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AttendanceAnalyticsService analyticsService;
    private final AttendanceExportService exportService;

    @Operation(summary = "Get course-level attendance analytics")
    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','SUPER_ADMIN')")
    public ResponseEntity<AttendanceAnalyticsDto> getCourseAnalytics(@PathVariable String courseId) {
        return ResponseEntity.ok(analyticsService.buildCourseAnalytics(courseId));
    }

    @Operation(summary = "Get student attendance report")
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','SUPER_ADMIN')")
    public ResponseEntity<StudentAttendanceReport> getStudentReport(
            @PathVariable String studentId,
            @RequestParam(required = false) String courseId) {
        return ResponseEntity.ok(analyticsService.buildStudentReport(studentId, courseId));
    }

    @Operation(summary = "Export meeting attendance as Excel")
    @GetMapping("/export/meeting/{meetingId}/excel")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportMeetingExcel(@PathVariable String meetingId) throws IOException {
        byte[] data = exportService.exportMeetingAttendanceExcel(meetingId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendance_" + meetingId + ".xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(data);
    }

    @Operation(summary = "Export student report as Excel")
    @GetMapping("/export/student/{studentId}/excel")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','SUPER_ADMIN','STUDENT')")
    public ResponseEntity<byte[]> exportStudentExcel(
            @PathVariable String studentId,
            @RequestParam(required = false) String courseId,
            @RequestHeader("X-User-Id") String requesterId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String role) throws IOException {
        // Students can only export their own
        if ("student".equalsIgnoreCase(role) && !studentId.equals(requesterId)) {
            return ResponseEntity.status(403).build();
        }
        byte[] data = exportService.exportStudentReportExcel(studentId, courseId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"student_attendance_" + studentId + ".xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(data);
    }
}
