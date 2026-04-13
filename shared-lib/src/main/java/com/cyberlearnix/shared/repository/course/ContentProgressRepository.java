package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.ContentProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContentProgressRepository extends JpaRepository<ContentProgress, Long> {

    Optional<ContentProgress> findByStudentIdAndContentId(String studentId, Long contentId);

    List<ContentProgress> findByStudentId(String studentId);

    @Query("SELECT cp FROM ContentProgress cp " +
            "JOIN ModuleContent mc ON cp.contentId = mc.id " +
            "WHERE cp.studentId = :studentId AND mc.module.course.id = :courseId")
    List<ContentProgress> findByStudentIdAndCourseId(@Param("studentId") String studentId,
            @Param("courseId") Long courseId);

    @Query("SELECT COUNT(cp) FROM ContentProgress cp " +
            "JOIN ModuleContent mc ON cp.contentId = mc.id " +
            "WHERE cp.studentId = :studentId AND mc.module.course.id = :courseId AND cp.isCompleted = true")
    long countCompletedByStudentAndCourse(@Param("studentId") String studentId, @Param("courseId") Long courseId);
}
