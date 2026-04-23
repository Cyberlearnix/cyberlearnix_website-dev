package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
    List<QuestionOption> findByQuestionId(Long questionId);
}
