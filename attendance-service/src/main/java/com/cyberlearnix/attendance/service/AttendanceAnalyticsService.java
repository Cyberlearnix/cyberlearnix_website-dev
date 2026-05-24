package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.dto.*;
import com.cyberlearnix.attendance.entity.*;
import com.cyberlearnix.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceAnalyticsService {

    private final MeetingRepository meetingRepo;
    private final FinalAttendanceRepository finalAttRepo;
    private final MeetingSessionRepository sessionRepo;
    private final CertificateEligibilityRepository certRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public AttendanceAnalyticsDto buildCourseAnalytics(String courseId) {
        List<Meeting> meetings = meetingRepo.findByCourseIdOrderByScheduledStartDesc(courseId);

        List<FinalAttendance> allRecords = meetings.stream()
            .flatMap(m -> finalAttRepo.findByMeetingIdOrderByStudentNameAsc(m.getId()).stream())
            .toList();

        Set<String> students = allRecords.stream().map(FinalAttendance::getStudentId).collect(Collectors.toSet());

        double avgPct = allRecords.stream()
            .mapToDouble(FinalAttendance::getAttendancePercentage)
            .average().orElse(0.0);

        AttendanceAnalyticsDto dto = new AttendanceAnalyticsDto();
        dto.setCourseId(courseId);
        dto.setTotalMeetings(meetings.size());
        dto.setTotalStudents(students.size());
        dto.setAvgAttendancePercentage(avgPct);

        dto.setPresentCount(allRecords.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.PRESENT).count());
        dto.setPartialCount(allRecords.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.PARTIAL).count());
        dto.setLateCount(allRecords.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.LATE).count());
        dto.setAbsentCount(allRecords.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.ABSENT).count());
        dto.setExcusedCount(allRecords.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.EXCUSED).count());

        // Daily trend
        Map<String, List<FinalAttendance>> byDate = meetings.stream()
            .collect(Collectors.toMap(
                m -> m.getScheduledStart().format(DATE_FMT),
                m -> finalAttRepo.findByMeetingIdOrderByStudentNameAsc(m.getId()),
                (a, b) -> { a.addAll(b); return a; }
            ));

        List<AttendanceAnalyticsDto.TrendPoint> daily = byDate.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                AttendanceAnalyticsDto.TrendPoint tp = new AttendanceAnalyticsDto.TrendPoint();
                tp.setDate(e.getKey());
                tp.setAvgPercentage(e.getValue().stream().mapToDouble(FinalAttendance::getAttendancePercentage).average().orElse(0.0));
                tp.setTotalStudents(e.getValue().size());
                tp.setPresentCount((int) e.getValue().stream().filter(r -> r.getStatus() != FinalAttendance.AttendanceStatus.ABSENT).count());
                return tp;
            })
            .toList();
        dto.setDailyTrend(daily);

        // Per-student ranking
        Map<String, List<FinalAttendance>> byStudent = allRecords.stream()
            .collect(Collectors.groupingBy(FinalAttendance::getStudentId));

        List<AttendanceAnalyticsDto.StudentRank> ranks = byStudent.entrySet().stream()
            .map(e -> {
                AttendanceAnalyticsDto.StudentRank rank = new AttendanceAnalyticsDto.StudentRank();
                rank.setStudentId(e.getKey());
                rank.setStudentName(e.getValue().get(0).getStudentName());
                rank.setStudentEmail(e.getValue().get(0).getStudentEmail());
                rank.setTotalMeetings(e.getValue().size());
                rank.setAttendedCount((int) e.getValue().stream()
                    .filter(r -> r.getStatus() != FinalAttendance.AttendanceStatus.ABSENT).count());
                rank.setAvgPercentage(e.getValue().stream()
                    .mapToDouble(FinalAttendance::getAttendancePercentage).average().orElse(0.0));
                certRepo.findByStudentIdAndCourseId(e.getKey(), courseId)
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
                List<FinalAttendance> mRecords = finalAttRepo.findByMeetingIdOrderByStudentNameAsc(m.getId());
                AttendanceAnalyticsDto.MeetingBreakdown b = new AttendanceAnalyticsDto.MeetingBreakdown();
                b.setMeetingId(m.getId());
                b.setMeetingTitle(m.getTitle());
                b.setScheduledStart(m.getScheduledStart() != null ? m.getScheduledStart().toString() : null);
                b.setTotalInvited(mRecords.size());
                b.setTotalJoined((int) mRecords.stream().filter(r -> r.getStatus() != FinalAttendance.AttendanceStatus.ABSENT).count());
                b.setAvgAttendancePercent(mRecords.stream().mapToDouble(FinalAttendance::getAttendancePercentage).average().orElse(0.0));
                b.setPresentCount(mRecords.stream().filter(r -> r.getStatus() != FinalAttendance.AttendanceStatus.ABSENT).count());
                b.setAbsentCount(mRecords.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.ABSENT).count());
                return b;
            })
            .toList();
        dto.setMeetingBreakdown(breakdowns);

        return dto;
    }

    public StudentAttendanceReport buildStudentReport(String studentId, String courseId) {
        List<FinalAttendance> records;
        if (courseId != null && !courseId.isBlank()) {
            records = finalAttRepo.findByCourseAndStudent(courseId, studentId);
        } else {
            records = finalAttRepo.findByStudentIdOrderByCreatedAtDesc(studentId);
        }

        StudentAttendanceReport report = new StudentAttendanceReport();
        report.setStudentId(studentId);
        report.setCourseId(courseId);
        report.setTotalMeetings(records.size());

        if (!records.isEmpty()) {
            report.setStudentName(records.get(0).getStudentName());
            report.setStudentEmail(records.get(0).getStudentEmail());
        }

        report.setPresentCount((int) records.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.PRESENT).count());
        report.setPartialCount((int) records.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.PARTIAL).count());
        report.setLateCount((int) records.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.LATE).count());
        report.setAbsentCount((int) records.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.ABSENT).count());
        report.setExcusedCount((int) records.stream().filter(r -> r.getStatus() == FinalAttendance.AttendanceStatus.EXCUSED).count());

        double overall = records.stream().mapToDouble(FinalAttendance::getAttendancePercentage).average().orElse(0.0);
        report.setOverallPercentage(overall);

        if (courseId != null) {
            certRepo.findByStudentIdAndCourseId(studentId, courseId).ifPresent(c -> {
                report.setCertificateEligible(c.getEligible());
                report.setIneligibilityReason(c.getIneligibilityReason());
            });
        }

        // Map to DTOs
        report.setSessions(records.stream().map(this::toDto).toList());
        return report;
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
        dto.setOverrideBy(fa.getOverrideBy());
        dto.setOverrideAt(fa.getOverrideAt());
        dto.setOverrideReason(fa.getOverrideReason());
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
