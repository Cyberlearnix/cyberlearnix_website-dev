package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.dto.AttendanceResponse;
import com.cyberlearnix.attendance.dto.MeetingReportResponse;
import com.cyberlearnix.attendance.entity.Meeting;
import com.cyberlearnix.attendance.entity.MeetingAttendance;
import com.cyberlearnix.attendance.entity.MeetingAttendance.AttendanceStatus;
import com.cyberlearnix.attendance.entity.MeetingParticipant;
import com.cyberlearnix.attendance.repository.MeetingAttendanceRepository;
import com.cyberlearnix.attendance.repository.MeetingParticipantRepository;
import com.cyberlearnix.attendance.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final MeetingRepository meetingRepository;
    private final MeetingAttendanceRepository attendanceRepository;
    private final MeetingParticipantRepository participantRepository;

    @Transactional
    public AttendanceResponse recordJoin(String meetingId, String studentId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));

        // Create a new meeting attendance session
        MeetingAttendance attendance = new MeetingAttendance();
        attendance.setMeetingId(meetingId);
        attendance.setStudentId(studentId);
        attendance.setJoinTime(LocalDateTime.now());
        attendance.setAttendanceStatus(AttendanceStatus.ABSENT); // default status until leave is recorded

        MeetingAttendance saved = attendanceRepository.save(attendance);
        log.info("Recorded student join: studentId={}, meetingId={}", studentId, meetingId);

        // Also track participant in meeting_participants
        if (participantRepository.findByMeetingIdAndUserId(meetingId, studentId).isEmpty()) {
            MeetingParticipant participant = new MeetingParticipant();
            participant.setMeetingId(meetingId);
            participant.setUserId(studentId);
            participant.setRole("ROLE_STUDENT");
            participantRepository.save(participant);
        }

        // Auto start meeting if it is in SCHEDULED status
        if (meeting.getStatus() == Meeting.MeetingStatus.SCHEDULED) {
            meeting.setStatus(Meeting.MeetingStatus.LIVE);
            meetingRepository.save(meeting);
            log.info("Meeting {} status moved to LIVE automatically on student join.", meetingId);
        }

        return mapToResponse(saved);
    }

    @Transactional
    public AttendanceResponse recordLeave(String meetingId, String studentId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));

        // Find the latest active session for this student in this meeting
        MeetingAttendance attendance = attendanceRepository
                .findTopByMeetingIdAndStudentIdOrderByJoinTimeDesc(meetingId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("No join record found for student " + studentId + " in meeting " + meetingId));

        LocalDateTime leaveTime = LocalDateTime.now();
        attendance.setLeaveTime(leaveTime);

        // Calculate duration in minutes
        long durationMinutes = Duration.between(attendance.getJoinTime(), leaveTime).toMinutes();
        attendance.setDurationMinutes((int) durationMinutes);

        // Calculate meeting duration
        long meetingDuration = Duration.between(meeting.getStartTime(), meeting.getEndTime()).toMinutes();
        if (meetingDuration <= 0) {
            meetingDuration = 60; // fallback to 60 minutes default
        }

        // Calculate attendance percentage
        double pct = ((double) durationMinutes / meetingDuration) * 100.0;
        attendance.setAttendancePercentage(Math.min(100.0, pct));

        // Determine status: 90%+ Present, 75%-89% Late, below 75% Absent
        if (pct >= 90.0) {
            attendance.setAttendanceStatus(AttendanceStatus.PRESENT);
        } else if (pct >= 75.0) {
            attendance.setAttendanceStatus(AttendanceStatus.LATE);
        } else {
            attendance.setAttendanceStatus(AttendanceStatus.ABSENT);
        }

        MeetingAttendance saved = attendanceRepository.save(attendance);
        log.info("Recorded student leave: studentId={}, meetingId={}, duration={}, status={}",
                studentId, meetingId, durationMinutes, attendance.getAttendanceStatus());

        return mapToResponse(saved);
    }

    public MeetingReportResponse getMeetingAttendanceReport(String meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));

        List<MeetingAttendance> attendanceList = attendanceRepository.findByMeetingIdOrderByStudentIdAsc(meetingId);

        MeetingReportResponse report = new MeetingReportResponse();
        report.setMeetingId(meetingId);
        report.setTitle(meeting.getTitle());
        report.setCourseId(meeting.getCourseId());
        report.setFacultyId(meeting.getFacultyId());
        report.setTotalParticipants(attendanceList.size());

        double avgPercentage = attendanceList.stream()
                .mapToDouble(a -> a.getAttendancePercentage() != null ? a.getAttendancePercentage() : 0.0)
                .average()
                .orElse(0.0);
        report.setAverageAttendancePercentage(avgPercentage);

        List<AttendanceResponse> dtoList = attendanceList.stream()
                .map(this::mapToResponse)
                .toList();
        report.setStudentAttendanceList(dtoList);

        return report;
    }

    private AttendanceResponse mapToResponse(MeetingAttendance a) {
        AttendanceResponse resp = new AttendanceResponse();
        resp.setId(a.getId());
        resp.setMeetingId(a.getMeetingId());
        resp.setStudentId(a.getStudentId());
        resp.setJoinTime(a.getJoinTime());
        resp.setLeaveTime(a.getLeaveTime());
        resp.setDurationMinutes(a.getDurationMinutes());
        resp.setAttendancePercentage(a.getAttendancePercentage());
        resp.setAttendanceStatus(a.getAttendanceStatus() != null ? a.getAttendanceStatus().name() : null);
        resp.setCreatedAt(a.getCreatedAt());
        return resp;
    }
}
