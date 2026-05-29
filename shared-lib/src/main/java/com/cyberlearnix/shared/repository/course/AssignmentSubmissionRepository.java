package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {

    List<AssignmentSubmission> findByContentIdOrderBySubmittedAtDesc(Long contentId);

    Optional<AssignmentSubmission> findByStudentIdAndContentIdAndAttemptNumber(
            String studentId, Long contentId, Integer attemptNumber);

    List<AssignmentSubmission> findByStudentIdAndContentId(String studentId, Long contentId);

    List<AssignmentSubmission> findByContentIdAndStatus(Long contentId, String status);

    long countByContentId(Long contentId);

    long countByContentIdAndStatus(Long contentId, String status);

    @Query("SELECT s FROM AssignmentSubmission s WHERE s.contentId = :contentId ORDER BY s.submittedAt DESC")
    List<AssignmentSubmission> findAllForContent(@Param("contentId") Long contentId);

    @Query("SELECT AVG(s.score) FROM AssignmentSubmission s WHERE s.contentId = :contentId AND s.score IS NOT NULL")
    Double findAvgScoreByContentId(@Param("contentId") Long contentId);

    @Query("SELECT s.score FROM AssignmentSubmission s WHERE s.contentId = :contentId AND s.score IS NOT NULL")
    List<Integer> findScoresByContentId(@Param("contentId") Long contentId);

    @Query("SELECT COUNT(s) FROM AssignmentSubmission s WHERE s.contentId = :contentId AND s.plagiarismScore >= :threshold")
    long countPlagiarismFlagged(@Param("contentId") Long contentId, @Param("threshold") int threshold);
}
