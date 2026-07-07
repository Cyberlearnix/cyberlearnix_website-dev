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

    Optional<Meeting> findByMeetingCode(String meetingCode);

    List<Meeting> findByCourseId(Long courseId);

    @Query("SELECT m FROM Meeting m WHERE m.courseId = :courseId ORDER BY m.startTime DESC")
    Page<Meeting> findByCourseIdPaged(@Param("courseId") Long courseId, Pageable pageable);

    @Query("SELECT m FROM Meeting m WHERE m.courseId IN :courseIds AND m.status <> 'CANCELLED' ORDER BY m.startTime ASC")
    List<Meeting> findByCourseIds(@Param("courseIds") List<Long> courseIds);

    @Query("SELECT m FROM Meeting m WHERE m.status = 'LIVE'")
    List<Meeting> findLiveMeetings();

    @Query("SELECT m FROM Meeting m WHERE m.startTime >= :from AND m.startTime <= :to AND m.status <> 'CANCELLED'")
    List<Meeting> findUpcomingMeetings(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
