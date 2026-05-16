package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.LiveSessionContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LiveSessionContentRepository extends JpaRepository<LiveSessionContent, Long> {
}
