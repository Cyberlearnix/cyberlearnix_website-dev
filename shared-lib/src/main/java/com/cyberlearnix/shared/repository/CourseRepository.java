package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.course.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByCreatedBy(String createdBy);
    long countByIsActive(Boolean isActive);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM Course c WHERE LOWER(c.status) = LOWER(:status)")
    long countByStatusIgnoreCase(String status);
}
