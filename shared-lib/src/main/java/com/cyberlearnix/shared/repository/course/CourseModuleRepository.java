package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.CourseModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {
    
    @EntityGraph(attributePaths = {"contents"})
    List<CourseModule> findByCourseIdOrderByOrderIndex(Long courseId);
    
    @EntityGraph(attributePaths = {"contents"})
    Optional<CourseModule> findByIdAndCourseId(Long id, Long courseId);
    
    List<CourseModule> findByCourseIdAndIsActiveOrderByOrderIndex(Long courseId, Boolean isActive);
    
    @Query("SELECT COUNT(m) FROM CourseModule m WHERE m.course.id = :courseId")
    Long countByCourseId(@Param("courseId") Long courseId);
    
    @Query("SELECT MAX(m.orderIndex) FROM CourseModule m WHERE m.course.id = :courseId")
    Integer findMaxOrderIndexByCourseId(@Param("courseId") Long courseId);
}
