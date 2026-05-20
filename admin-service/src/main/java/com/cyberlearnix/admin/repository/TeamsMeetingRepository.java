package com.cyberlearnix.admin.repository;

import com.cyberlearnix.admin.entity.TeamsMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeamsMeetingRepository extends JpaRepository<TeamsMeeting, Long> {

    List<TeamsMeeting> findAllByStatusOrderByStartDateTimeAsc(String status);
    List<TeamsMeeting> findAllByCourseIdOrderByStartDateTimeAsc(Long courseId);

    Optional<TeamsMeeting> findByGraphMeetingId(String graphMeetingId);

    /**
     * Meetings whose end time has passed (with a small buffer) but whose
     * Zoho attendance has not yet been fetched. Used by the participant
     * sync scheduler. Skips meetings older than `oldest` to avoid pounding
     * Zoho for ancient records.
     */
    @Query("SELECT m FROM TeamsMeeting m " +
            "WHERE m.status = 'SCHEDULED' " +
            "AND m.endDateTime < :cutoff " +
            "AND m.endDateTime > :oldest " +
            "AND m.participantsSyncedAt IS NULL " +
            "AND m.graphMeetingId IS NOT NULL " +
            "ORDER BY m.endDateTime DESC")
    List<TeamsMeeting> findForParticipantSync(@Param("cutoff") LocalDateTime cutoff,
                                              @Param("oldest") LocalDateTime oldest);
}