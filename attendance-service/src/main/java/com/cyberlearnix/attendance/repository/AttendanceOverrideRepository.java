package com.cyberlearnix.attendance.repository;

import com.cyberlearnix.attendance.entity.AttendanceOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceOverrideRepository extends JpaRepository<AttendanceOverride, String> {

    List<AttendanceOverride> findByFinalAttendanceIdOrderByCreatedAtDesc(String finalAttendanceId);

    List<AttendanceOverride> findByMeetingIdOrderByCreatedAtDesc(String meetingId);

    List<AttendanceOverride> findByAdminIdOrderByCreatedAtDesc(String adminId);
}
