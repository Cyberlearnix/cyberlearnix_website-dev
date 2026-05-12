package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByCourseId(Long courseId);

    List<Exam> findByStatus(String status);

    List<Exam> findByCreatedBy(String createdBy);

    List<Exam> findByCourseIdAndStatus(Long courseId, String status);

    @Query("SELECT e FROM Exam e WHERE e.courseId = :courseId AND e.status = 'PUBLISHED'")
    List<Exam> findPublishedByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(e) FROM Exam e WHERE e.status = :status")
    Long countByStatus(@Param("status") String status);
}
