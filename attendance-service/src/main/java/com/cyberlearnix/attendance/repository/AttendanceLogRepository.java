package com.cyberlearnix.attendance.repository;

import com.cyberlearnix.attendance.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, String> {

    List<AttendanceLog> findByMeetingIdOrderByOccurredAtAsc(String meetingId);

    List<AttendanceLog> findByMeetingIdAndStudentIdOrderByOccurredAtAsc(String meetingId, String studentId);

    List<AttendanceLog> findByMeetingIdAndEventTypeOrderByOccurredAtAsc(String meetingId, String eventType);

    long countByMeetingIdAndEventType(String meetingId, String eventType);

    List<AttendanceLog> findByOccurredAtBetweenOrderByOccurredAtAsc(LocalDateTime from, LocalDateTime to);
}
