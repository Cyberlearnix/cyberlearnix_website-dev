package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.entity.CertificateEligibility;
import com.cyberlearnix.attendance.entity.FinalAttendance;
import com.cyberlearnix.attendance.entity.Meeting;
import com.cyberlearnix.attendance.repository.CertificateEligibilityRepository;
import com.cyberlearnix.attendance.repository.FinalAttendanceRepository;
import com.cyberlearnix.attendance.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateEligibilityService {

    private final CertificateEligibilityRepository certRepo;
    private final FinalAttendanceRepository finalAttRepo;
    private final MeetingRepository meetingRepo;

    @Lazy
    @Autowired
    private CertificateEligibilityService self;

    @Value("${attendance.certificate-min-percent:80}")
    private double certMinPercent;

    @Transactional
    public void recalculateForCourse(String courseId) {
        log.info("Recalculating certificate eligibility for course: {}", courseId);

        List<Meeting> meetings = meetingRepo.findByCourseIdOrderByScheduledStartDesc(courseId);
        long totalMandatory = meetings.stream().filter(m -> Boolean.TRUE.equals(m.getMandatory())).count();

        // Group all attendance records by student
        List<FinalAttendance> allAttendance = meetings.stream()
            .flatMap(m -> finalAttRepo.findByMeetingIdOrderByStudentNameAsc(m.getId()).stream())
            .toList();

        Map<String, List<FinalAttendance>> byStudent = allAttendance.stream()
            .collect(Collectors.groupingBy(FinalAttendance::getStudentId));

        byStudent.forEach((studentId, records) ->
            self.recalculateForStudentAndCourse(studentId, courseId, records, meetings.size(), (int) totalMandatory));
    }

    @Transactional
    public CertificateEligibility recalculateForStudentAndCourse(
            String studentId, String courseId,
            List<FinalAttendance> records, int totalMeetings, int mandatoryMeetings) {

        long attended = records.stream()
            .filter(r -> r.getStatus() != FinalAttendance.AttendanceStatus.ABSENT)
            .count();

        long mandatoryAttended = records.stream()
            .filter(r -> r.getCountsForCertificate() != null && r.getCountsForCertificate())
            .count();

        double overallPct = totalMeetings > 0 ? (double) attended / totalMeetings * 100.0 : 0.0;

        boolean meetsReq = overallPct >= certMinPercent;
        boolean eligible = meetsReq;
        String reason = null;
        if (!meetsReq) {
            reason = String.format("Attendance %.1f%% is below required %.0f%%", overallPct, certMinPercent);
        }

        CertificateEligibility ce = certRepo.findByStudentIdAndCourseId(studentId, courseId)
            .orElseGet(() -> {
                CertificateEligibility newCe = new CertificateEligibility();
                newCe.setStudentId(studentId);
                newCe.setCourseId(courseId);
                // populate name/email from first record
                records.stream().findFirst().ifPresent(r -> {
                    newCe.setStudentName(r.getStudentName());
                    newCe.setStudentEmail(r.getStudentEmail());
                });
                return newCe;
            });

        ce.setTotalMeetings(totalMeetings);
        ce.setAttendedMeetings((int) attended);
        ce.setMandatoryMeetings(mandatoryMeetings);
        ce.setMandatoryAttended((int) mandatoryAttended);
        ce.setOverallAttendancePercentage(overallPct);
        ce.setMeetsAttendanceRequirement(meetsReq);
        ce.setEligible(eligible);
        ce.setIneligibilityReason(reason);

        return certRepo.save(ce);
    }
}
