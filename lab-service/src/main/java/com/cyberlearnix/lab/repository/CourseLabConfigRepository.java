package com.cyberlearnix.lab.repository;

import com.cyberlearnix.lab.entity.CourseLabConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseLabConfigRepository extends JpaRepository<CourseLabConfig, Long> {

    List<CourseLabConfig> findByCourseIdAndIsActiveTrue(Long courseId);

    List<CourseLabConfig> findByIsActiveTrue();

    boolean existsByCourseIdAndLabTemplateId(Long courseId, Long templateId);

    Optional<CourseLabConfig> findByCourseId(Long courseId);

    Optional<CourseLabConfig> findByCourseIdAndLabTemplateId(Long courseId, Long templateId);
}
