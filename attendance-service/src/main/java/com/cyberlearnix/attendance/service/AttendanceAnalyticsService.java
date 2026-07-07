package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.dto.*;
import com.cyberlearnix.attendance.entity.*;
import com.cyberlearnix.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceAnalyticsService {

    private final MeetingRepository meetingRepo;
    private final MeetingAttendanceRepository attendanceRepo;
    private final CertificateEligibilityRepository certRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public AttendanceAnalyticsDto buildCourseAnalytics(String courseIdStr) {
        Long courseId = Long.parseLong(courseIdStr);
        List<Meeting> meetings = meetingRepo.findByCourseId(courseId);

        List<MeetingAttendance> allRecords = meetings.stream()
            .flatMap(m -> attendanceRepo.findByMeetingIdOrderByStudentIdAsc(m.getId()).stream())
            .toList();

        Set<String> students = allRecords.stream().map(MeetingAttendance::getStudentId).collect(Collectors.toSet());

        double avgPct = allRecords.stream()
            .mapToDouble(a -> a.getAttendancePercentage() != null ? a.getAttendancePercentage() : 0.0)
            .average().orElse(0.0);

        AttendanceAnalyticsDto dto = new AttendanceAnalyticsDto();
        dto.setCourseId(courseIdStr);
        dto.setTotalMeetings(meetings.size());
        dto.setTotalStudents(students.size());
        dto.setAvgAttendancePercentage(avgPct);

        dto.setPresentCount(allRecords.stream().filter(r -> r.getAttendanceStatus() == MeetingAttendance.AttendanceStatus.PRESENT).count());
        dto.setPartialCount(0L);
        dto.setLateCount(allRecords.stream().filter(r -> r.getAttendanceStatus() == MeetingAttendance.AttendanceStatus.LATE).count());
        dto.setAbsentCount(allRecords.stream().filter(r -> r.getAttendanceStatus() == MeetingAttendance.AttendanceStatus.ABSENT).count());
        dto.setExcusedCount(0L);

        // Daily trend
        Map<String, List<MeetingAttendance>> byDate = meetings.stream()
            .collect(Collectors.toMap(
                m -> m.getStartTime().format(DATE_FMT),
                m -> attendanceRepo.findByMeetingId(m.getId()),
                (a, b) -> { a.addAll(b); return a; }
            ));

        List<AttendanceAnalyticsDto.TrendPoint> daily = byDate.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                AttendanceAnalyticsDto.TrendPoint tp = new AttendanceAnalyticsDto.TrendPoint();
                tp.setDate(e.getKey());
                tp.setAvgPercentage(e.getValue().stream().mapToDouble(a -> a.getAttendancePercentage() != null ? a.getAttendancePercentage() : 0.0).average().orElse(0.0));
                tp.setTotalStudents(e.getValue().size());
                tp.setPresentCount((int) e.getValue().stream().filter(r -> r.getAttendanceStatus() != MeetingAttendance.AttendanceStatus.ABSENT).count());
                return tp;
            })
            .toList();
        dto.setDailyTrend(daily);

        // Per-student ranking
        Map<String, List<MeetingAttendance>> byStudent = allRecords.stream()
            .collect(Collectors.groupingBy(MeetingAttendance::getStudentId));

        List<AttendanceAnalyticsDto.StudentRank> ranks = byStudent.entrySet().stream()
            .map(e -> {
                AttendanceAnalyticsDto.StudentRank rank = new AttendanceAnalyticsDto.StudentRank();
                rank.setStudentId(e.getKey());
                rank.setStudentName(e.getKey());
                rank.setStudentEmail(e.getKey());
                rank.setTotalMeetings(e.getValue().size());
                rank.setAttendedCount((int) e.getValue().stream()
                    .filter(r -> r.getAttendanceStatus() != MeetingAttendance.AttendanceStatus.ABSENT).count());
                rank.setAvgPercentage(e.getValue().stream()
                    .mapToDouble(a -> a.getAttendancePercentage() != null ? a.getAttendancePercentage() : 0.0).average().orElse(0.0));
                certRepo.findByStudentIdAndCourseId(e.getKey(), courseIdStr)
                    .ifPresent(c -> rank.setEligibilityStatus(c.getEligible() ? "ELIGIBLE" : "INELIGIBLE"));
                return rank;
            })
            .sorted(Comparator.comparingDouble(AttendanceAnalyticsDto.StudentRank::getAvgPercentage).reversed())
            .toList();

        dto.setTopPerformers(ranks.stream().limit(10).toList());
        dto.setAtRiskStudents(ranks.stream()
            .filter(r -> r.getAvgPercentage() < 60.0)
            .sorted(Comparator.comparingDouble(AttendanceAnalyticsDto.StudentRank::getAvgPercentage))
            .limit(20)
            .toList());

        // Meeting breakdown
        List<AttendanceAnalyticsDto.MeetingBreakdown> breakdowns = meetings.stream()
            .map(m -> {
                List<MeetingAttendance> mRecords = attendanceRepo.findByMeetingId(m.getId());
                AttendanceAnalyticsDto.MeetingBreakdown b = new AttendanceAnalyticsDto.MeetingBreakdown();
                b.setMeetingId(m.getId());
                b.setMeetingTitle(m.getTitle());
                b.setScheduledStart(m.getStartTime() != null ? m.getStartTime().toString() : null);
                b.setTotalInvited(mRecords.size());
                b.setTotalJoined((int) mRecords.stream().filter(r -> r.getAttendanceStatus() != MeetingAttendance.AttendanceStatus.ABSENT).count());
                b.setAvgAttendancePercent(mRecords.stream().mapToDouble(a -> a.getAttendancePercentage() != null ? a.getAttendancePercentage() : 0.0).average().orElse(0.0));
                b.setPresentCount(mRecords.stream().filter(r -> r.getAttendanceStatus() != MeetingAttendance.AttendanceStatus.ABSENT).count());
                b.setAbsentCount(mRecords.stream().filter(r -> r.getAttendanceStatus() == MeetingAttendance.AttendanceStatus.ABSENT).count());
                return b;
            })
            .toList();
        dto.setMeetingBreakdown(breakdowns);

        return dto;
    }

    public StudentAttendanceReport buildStudentReport(String studentId, String courseId) {
        List<MeetingAttendance> records = attendanceRepo.findByStudentId(studentId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        if (courseId != null && !courseId.isBlank()) {
            records = records.stream()
                .filter(r -> {
                    Optional<Meeting> m = meetingRepo.findById(r.getMeetingId());
                    return m.isPresent() && String.valueOf(m.get().getCourseId()).equals(courseId);
                })
                .toList();
        }

        StudentAttendanceReport report = new StudentAttendanceReport();
        report.setStudentId(studentId);
        report.setCourseId(courseId);
        report.setTotalMeetings(records.size());

        if (!records.isEmpty()) {
            report.setStudentName(studentId);
            report.setStudentEmail(studentId);
        }

        report.setPresentCount((int) records.stream().filter(r -> r.getAttendanceStatus() == MeetingAttendance.AttendanceStatus.PRESENT).count());
        report.setPartialCount(0);
        report.setLateCount((int) records.stream().filter(r -> r.getAttendanceStatus() == MeetingAttendance.AttendanceStatus.LATE).count());
        report.setAbsentCount((int) records.stream().filter(r -> r.getAttendanceStatus() == MeetingAttendance.AttendanceStatus.ABSENT).count());
        report.setExcusedCount(0);

        double overall = records.stream().mapToDouble(a -> a.getAttendancePercentage() != null ? a.getAttendancePercentage() : 0.0).average().orElse(0.0);
        report.setOverallPercentage(overall);

        if (courseId != null) {
            certRepo.findByStudentIdAndCourseId(studentId, courseId).ifPresent(c -> {
                report.setCertificateEligible(c.getEligible());
                report.setIneligibilityReason(c.getIneligibilityReason());
            });
        }

        report.setSessions(records.stream().map(this::toDto).toList());
        return report;
    }

    private AttendanceDto toDto(MeetingAttendance a) {
        AttendanceDto dto = new AttendanceDto();
        dto.setId(a.getId());
        dto.setMeetingId(a.getMeetingId());
        dto.setStudentId(a.getStudentId());
        dto.setStudentName(a.getStudentId());
        dto.setStudentEmail(a.getStudentId());
        dto.setTotalActiveSeconds(a.getDurationMinutes() != null ? (long) a.getDurationMinutes() * 60 : 0L);
        dto.setMeetingDurationSeconds(3600L);
        dto.setAttendancePercentage(a.getAttendancePercentage());
        dto.setStatus(a.getAttendanceStatus());
        dto.setRejoinCount(0);
        dto.setLate(a.getAttendanceStatus() == MeetingAttendance.AttendanceStatus.LATE);
        dto.setLateByMinutes(0);
        dto.setOverridden(false);
        dto.setLocked(false);
        dto.setCountsForCertificate(a.getAttendanceStatus() != MeetingAttendance.AttendanceStatus.ABSENT);
        dto.setAdminNotes("");
        dto.setCreatedAt(a.getCreatedAt());
        dto.setUpdatedAt(a.getCreatedAt());

        meetingRepo.findById(a.getMeetingId()).ifPresent(m -> {
            dto.setMeetingTitle(m.getTitle());
            dto.setMeetingScheduledStart(m.getStartTime());
            long durSec = Duration.between(m.getStartTime(), m.getEndTime()).toSeconds();
            dto.setMeetingDurationSeconds(durSec > 0 ? durSec : 3600L);
        });
        return dto;
    }
}
