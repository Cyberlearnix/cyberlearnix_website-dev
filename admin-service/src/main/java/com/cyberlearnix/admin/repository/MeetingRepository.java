package com.cyberlearnix.admin.repository;

import com.cyberlearnix.admin.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, String> {

    List<Meeting> findByCourseId(Long courseId);

    List<Meeting> findAllByOrderByStartTimeDesc();
}
