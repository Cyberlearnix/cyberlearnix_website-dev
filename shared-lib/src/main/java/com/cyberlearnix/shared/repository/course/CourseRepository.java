package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByCreatedBy(String createdBy);
    long countByIsActive(Boolean isActive);

    Page<Course> findAll(Pageable pageable);
    Page<Course> findByIsActive(Boolean isActive, Pageable pageable);
    Page<Course> findByCreatedBy(String createdBy, Pageable pageable);
    Page<Course> findByCreatedByAndIsActive(String createdBy, Boolean isActive, Pageable pageable);

    Page<Course> findByStatus(String status, Pageable pageable);
    Page<Course> findByStatusAndCreatedBy(String status, String createdBy, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM Course c WHERE c.id IN :ids")
    Page<Course> findByIdIn(@org.springframework.data.repository.query.Param("ids") List<Long> ids, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM Course c WHERE c.id IN :ids AND c.isActive = :isActive")
    Page<Course> findByIdInAndIsActive(@org.springframework.data.repository.query.Param("ids") List<Long> ids,
            @org.springframework.data.repository.query.Param("isActive") Boolean isActive, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM Course c WHERE LOWER(c.status) = LOWER(:status)")
    long countByStatusIgnoreCase(@org.springframework.data.repository.query.Param("status") String status);
}
