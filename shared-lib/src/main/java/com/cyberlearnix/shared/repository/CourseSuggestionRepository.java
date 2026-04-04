package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.CourseSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CourseSuggestionRepository extends JpaRepository<CourseSuggestion, Long> {
    List<CourseSuggestion> findByCourseId(Long courseId);

    List<CourseSuggestion> findByCourseIdIn(List<Long> courseIds);
}
