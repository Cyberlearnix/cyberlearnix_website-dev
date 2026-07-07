package com.cyberlearnix.attendance.repository;

import com.cyberlearnix.attendance.entity.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, String> {

    List<MeetingParticipant> findByMeetingId(String meetingId);

    Optional<MeetingParticipant> findByMeetingIdAndUserId(String meetingId, String userId);
}
