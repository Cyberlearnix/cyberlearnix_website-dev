package com.cyberlearnix.admin.repository;

import com.cyberlearnix.admin.entity.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findByMeetingIdOrderByJoinTimeAsc(Long meetingId);

    long countByMeetingId(Long meetingId);

    @Modifying
    @Transactional
    void deleteByMeetingId(Long meetingId);
}