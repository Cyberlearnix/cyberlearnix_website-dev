package com.cyberlearnix.attendance.repository;

import com.cyberlearnix.attendance.entity.FinalAttendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinalAttendanceRepository extends JpaRepository<FinalAttendance, String> {

    Optional<FinalAttendance> findByMeetingIdAndStudentId(String meetingId, String studentId);

    List<FinalAttendance> findByMeetingIdOrderByStudentNameAsc(String meetingId);

    List<FinalAttendance> findByStudentIdOrderByCreatedAtDesc(String studentId);

    Page<FinalAttendance> findByStudentId(String studentId, Pageable pageable);

    List<FinalAttendance> findByMeetingIdAndStatus(String meetingId, FinalAttendance.AttendanceStatus status);

    @Query("SELECT fa FROM FinalAttendance fa WHERE fa.studentId = :studentId AND fa.status IN ('PRESENT','PARTIAL','LATE')")
    List<FinalAttendance> findAttendedSessionsByStudent(@Param("studentId") String studentId);

    @Query("SELECT COUNT(fa) FROM FinalAttendance fa WHERE fa.meetingId = :meetingId AND fa.status IN ('PRESENT','PARTIAL','LATE')")
    long countAttendedByMeeting(@Param("meetingId") String meetingId);

    @Query("SELECT AVG(fa.attendancePercentage) FROM FinalAttendance fa WHERE fa.studentId = :studentId")
    Double avgAttendancePercentageByStudent(@Param("studentId") String studentId);

    @Query("SELECT fa FROM FinalAttendance fa WHERE fa.studentId = :studentId ORDER BY fa.createdAt DESC")
    Page<FinalAttendance> findByStudentIdPaged(@Param("studentId") String studentId, Pageable pageable);

    /** Students at risk — below threshold */
    @Query("SELECT fa.studentId, AVG(fa.attendancePercentage) as avg_pct FROM FinalAttendance fa GROUP BY fa.studentId HAVING AVG(fa.attendancePercentage) < :threshold ORDER BY avg_pct ASC")
    List<Object[]> findStudentsAtRisk(@Param("threshold") double threshold);

    @Query("SELECT fa FROM FinalAttendance fa WHERE fa.meetingId IN (SELECT m.id FROM Meeting m WHERE m.courseId = :courseId) AND fa.studentId = :studentId ORDER BY fa.createdAt DESC")
    List<FinalAttendance> findByCourseAndStudent(@Param("courseId") String courseId, @Param("studentId") String studentId);
}
