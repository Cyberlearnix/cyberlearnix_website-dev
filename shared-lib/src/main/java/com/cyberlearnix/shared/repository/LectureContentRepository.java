package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.course.LectureContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LectureContentRepository extends JpaRepository<LectureContent, Long> {
}
