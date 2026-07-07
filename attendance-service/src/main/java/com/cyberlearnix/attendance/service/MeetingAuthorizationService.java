package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.client.EnrollmentServiceClient;
import com.cyberlearnix.attendance.entity.Meeting;
import com.cyberlearnix.attendance.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingAuthorizationService {

    private final MeetingRepository meetingRepository;
    private final EnrollmentServiceClient enrollmentClient;

    public void authorizeStudentJoin(String meetingId, String studentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }

        // Allow Admins to skip check
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return;
        }

        // Allow Faculty/Teacher to skip check if they are the host
        boolean isFaculty = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_FACULTY") || a.getAuthority().equals("ROLE_TEACHER"));

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));

        if (isFaculty) {
            if (meeting.getFacultyId().equals(studentId)) {
                return;
            } else {
                throw new AccessDeniedException("Access Denied: You are not the assigned faculty for this meeting");
            }
        }

        // For student, check enrollment in course
        Boolean isEnrolled = enrollmentClient.checkEnrollment(studentId, meeting.getCourseId());
        if (isEnrolled == null || !isEnrolled) {
            log.warn("Access Denied: Student {} is not enrolled in course {}", studentId, meeting.getCourseId());
            throw new AccessDeniedException("Access Denied: You are not enrolled in the course associated with this meeting");
        }
    }
}
