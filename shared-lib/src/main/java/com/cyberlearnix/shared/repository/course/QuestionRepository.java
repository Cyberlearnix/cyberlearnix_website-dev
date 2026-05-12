package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByExamId(Long examId);

    List<Question> findByExamIdOrderByOrderIndex(Long examId);

    List<Question> findByIsActiveTrue();

    List<Question> findByIsActiveTrueAndQuestionType(String questionType);

    List<Question> findByIsActiveTrueAndDifficulty(String difficulty);

    List<Question> findByIsActiveTrueAndSubject(String subject);

    @Modifying
    @Transactional
    @Query("DELETE FROM Question q WHERE q.examId = :examId")
    void deleteByExamId(@Param("examId") Long examId);

    Long countByExamId(Long examId);

    @Query("SELECT q FROM Question q WHERE q.isActive = true AND q.questionType = :type AND q.difficulty = :difficulty ORDER BY FUNCTION('RANDOM')")
    List<Question> findRandomByTypeAndDifficulty(@Param("type") String type, @Param("difficulty") String difficulty);
}
