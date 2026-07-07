package com.cyberlearnix.attendance.repository;

import com.cyberlearnix.attendance.entity.MeetingAttendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingAttendanceRepository extends JpaRepository<MeetingAttendance, String> {

    List<MeetingAttendance> findByMeetingId(String meetingId);

    List<MeetingAttendance> findByMeetingIdOrderByStudentIdAsc(String meetingId);

    Page<MeetingAttendance> findByStudentId(String studentId, Pageable pageable);

    Optional<MeetingAttendance> findByMeetingIdAndStudentId(String meetingId, String studentId);

    Optional<MeetingAttendance> findTopByMeetingIdAndStudentIdOrderByJoinTimeDesc(String meetingId, String studentId);
}
