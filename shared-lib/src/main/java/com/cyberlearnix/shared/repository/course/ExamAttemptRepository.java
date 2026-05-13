package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    List<ExamAttempt> findByExamId(Long examId);

    List<ExamAttempt> findByStudentId(String studentId);

    List<ExamAttempt> findByExamIdAndStudentId(Long examId, String studentId);

    Optional<ExamAttempt> findByExamIdAndStudentIdAndStatus(Long examId, String studentId, String status);

    List<ExamAttempt> findByExamIdAndStatus(Long examId, String status);

    Long countByExamIdAndStudentId(Long examId, String studentId);

    Long countByExamIdAndStatus(Long examId, String status);

    @Query("SELECT a FROM ExamAttempt a WHERE a.examId = :examId AND a.status = 'IN_PROGRESS'")
    List<ExamAttempt> findLiveAttempts(@Param("examId") Long examId);

    @Query("SELECT AVG(a.percentage) FROM ExamAttempt a WHERE a.examId = :examId AND a.status IN ('SUBMITTED','GRADED')")
    Double findAverageScoreByExamId(@Param("examId") Long examId);

    @Query("SELECT COUNT(a) FROM ExamAttempt a WHERE a.examId = :examId AND a.passed = true")
    Long countPassedByExamId(@Param("examId") Long examId);
}
