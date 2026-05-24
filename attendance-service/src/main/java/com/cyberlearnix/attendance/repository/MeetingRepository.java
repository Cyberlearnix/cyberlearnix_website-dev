package com.cyberlearnix.attendance.repository;

import com.cyberlearnix.attendance.entity.Meeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, String> {

    Optional<Meeting> findByZohoMeetingId(String zohoMeetingId);

    List<Meeting> findByCourseIdOrderByScheduledStartDesc(String courseId);

    List<Meeting> findByBatchIdOrderByScheduledStartDesc(String batchId);

    List<Meeting> findByStatus(Meeting.MeetingStatus status);

    @Query("SELECT m FROM Meeting m WHERE m.status = 'SCHEDULED' AND m.scheduledStart BETWEEN :from AND :to ORDER BY m.scheduledStart")
    List<Meeting> findUpcomingMeetings(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT m FROM Meeting m WHERE m.status = 'LIVE' ORDER BY m.actualStart")
    List<Meeting> findLiveMeetings();

    @Query("SELECT m FROM Meeting m WHERE m.attendanceFinalized = false AND m.status = 'ENDED'")
    List<Meeting> findMeetingsNeedingFinalization();

    Page<Meeting> findByCourseIdAndStatusOrderByScheduledStartDesc(String courseId, Meeting.MeetingStatus status, Pageable pageable);

    @Query("SELECT COUNT(m) FROM Meeting m WHERE m.courseId = :courseId AND m.mandatory = true")
    long countMandatoryMeetingsByCourseId(@Param("courseId") String courseId);

    @Query("SELECT m FROM Meeting m WHERE m.courseId = :courseId ORDER BY m.scheduledStart DESC")
    Page<Meeting> findByCourseIdPaged(@Param("courseId") String courseId, Pageable pageable);
}
