package com.cyberlearnix.admin.repository;

import com.cyberlearnix.admin.entity.MeetingAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingAttendanceRepository extends JpaRepository<MeetingAttendance, Long> {

    List<MeetingAttendance> findByMeetingIdOrderByJoinTimeAsc(String meetingId);

    Optional<MeetingAttendance> findByMeetingIdAndStudentId(String meetingId, String studentId);

    long countByMeetingId(String meetingId);
}
