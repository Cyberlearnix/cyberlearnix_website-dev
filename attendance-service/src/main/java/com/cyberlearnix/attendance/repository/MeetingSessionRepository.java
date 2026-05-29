package com.cyberlearnix.attendance.repository;

import com.cyberlearnix.attendance.entity.MeetingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingSessionRepository extends JpaRepository<MeetingSession, String> {

    List<MeetingSession> findByMeetingIdOrderByJoinedAtAsc(String meetingId);

    List<MeetingSession> findByMeetingIdAndStudentIdOrderByJoinedAtAsc(String meetingId, String studentId);

    Optional<MeetingSession> findTopByMeetingIdAndStudentIdOrderByJoinedAtDesc(String meetingId, String studentId);

    /** Find all active sessions (still in meeting) */
    List<MeetingSession> findByMeetingIdAndSessionStatus(String meetingId, MeetingSession.SessionStatus status);

    /** All active sessions globally — for disconnect detection */
    List<MeetingSession> findBySessionStatus(MeetingSession.SessionStatus status);

    /** Sessions where heartbeat is stale — potential disconnects */
    @Query("SELECT s FROM MeetingSession s WHERE s.sessionStatus = 'ACTIVE' AND s.lastHeartbeat < :threshold")
    List<MeetingSession> findStaleActiveSessions(@Param("threshold") LocalDateTime threshold);

    /** Count of rejoin sessions for a student in a meeting */
    @Query("SELECT COUNT(s) FROM MeetingSession s WHERE s.meetingId = :meetingId AND s.studentId = :studentId")
    long countSessionsByMeetingAndStudent(@Param("meetingId") String meetingId, @Param("studentId") String studentId);

    /** Total active seconds per student per meeting */
    @Query("SELECT COALESCE(SUM(s.durationSeconds), 0) FROM MeetingSession s WHERE s.meetingId = :meetingId AND s.studentId = :studentId AND s.sessionStatus = 'COMPLETED'")
    Long sumActiveDurationByMeetingAndStudent(@Param("meetingId") String meetingId, @Param("studentId") String studentId);

    /** All unique students who joined a meeting */
    @Query("SELECT DISTINCT s.studentId FROM MeetingSession s WHERE s.meetingId = :meetingId")
    List<String> findDistinctStudentIdsByMeetingId(@Param("meetingId") String meetingId);

    /** Active live participants in a meeting */
    @Query("SELECT s FROM MeetingSession s WHERE s.meetingId = :meetingId AND s.sessionStatus = 'ACTIVE'")
    List<MeetingSession> findActiveLiveParticipants(@Param("meetingId") String meetingId);
}
