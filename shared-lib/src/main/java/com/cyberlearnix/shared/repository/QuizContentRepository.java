package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.course.QuizContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizContentRepository extends JpaRepository<QuizContent, Long> {
}
