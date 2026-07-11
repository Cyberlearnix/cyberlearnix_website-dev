package com.cyberlearnix.admin.repository;

import com.cyberlearnix.admin.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, String> {

    List<Meeting> findByCourseId(Long courseId);

    List<Meeting> findAllByOrderByStartTimeDesc();

    List<Meeting> findByCourseIdIn(List<Long> courseIds);

    @Query("SELECT m FROM Meeting m WHERE m.startTime >= :from AND m.startTime <= :to " +
           "AND m.status <> 'CANCELLED' ORDER BY m.startTime ASC")
    List<Meeting> findUpcomingMeetings(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    @Query("SELECT m FROM Meeting m WHERE m.startTime >= :from AND m.startTime <= :to " +
           "AND m.status = 'SCHEDULED'")
    List<Meeting> findMeetingsInWindow(@Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}
