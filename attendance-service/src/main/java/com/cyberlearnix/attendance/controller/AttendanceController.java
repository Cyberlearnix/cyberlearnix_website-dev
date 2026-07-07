package com.cyberlearnix.attendance.controller;

import com.cyberlearnix.attendance.dto.AttendanceResponse;
import com.cyberlearnix.attendance.dto.MeetingReportResponse;
import com.cyberlearnix.attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceResponse> joinMeeting(@RequestBody Map<String, String> body) {
        String meetingId = body.get("meetingId");
        String studentId = body.get("studentId");
        if (meetingId == null || studentId == null) {
            throw new IllegalArgumentException("meetingId and studentId are required");
        }
        return ResponseEntity.ok(attendanceService.recordJoin(meetingId, studentId));
    }

    @PostMapping("/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceResponse> leaveMeeting(@RequestBody Map<String, String> body) {
        String meetingId = body.get("meetingId");
        String studentId = body.get("studentId");
        if (meetingId == null || studentId == null) {
            throw new IllegalArgumentException("meetingId and studentId are required");
        }
        return ResponseEntity.ok(attendanceService.recordLeave(meetingId, studentId));
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY', 'TEACHER')")
    public ResponseEntity<MeetingReportResponse> getReport(@RequestParam String meetingId) {
        return ResponseEntity.ok(attendanceService.getMeetingAttendanceReport(meetingId));
    }
}
