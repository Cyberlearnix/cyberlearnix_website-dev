package com.cyberlearnix.admin.repository;

import com.cyberlearnix.admin.entity.TeamsMeeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamsMeetingRepository extends JpaRepository<TeamsMeeting, Long> {

    List<TeamsMeeting> findAllByStatusOrderByStartDateTimeAsc(String status);

    Optional<TeamsMeeting> findByGraphMeetingId(String graphMeetingId);
}
