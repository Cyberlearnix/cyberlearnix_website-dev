package com.cyberlearnix.lab.repository;

import com.cyberlearnix.lab.entity.CourseLabConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseLabConfigRepository extends JpaRepository<CourseLabConfig, Long> {

    List<CourseLabConfig> findByCourseIdAndIsActiveTrue(Long courseId);

    List<CourseLabConfig> findByIsActiveTrue();

    boolean existsByCourseIdAndLabTemplateId(Long courseId, Long templateId);
}
