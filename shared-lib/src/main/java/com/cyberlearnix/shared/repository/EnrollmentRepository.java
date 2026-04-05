package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.enrollment.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    
    @EntityGraph(attributePaths = {"course"})
    List<Enrollment> findByStudentId(String studentId);

    @EntityGraph(attributePaths = {"course"})
    Optional<Enrollment> findByStudentIdAndCourseId(String studentId, Long courseId);

    @EntityGraph(attributePaths = {"course"})
    List<Enrollment> findByCourseId(Long courseId);

    @Override
    @EntityGraph(attributePaths = {"course"})
    List<Enrollment> findAll();
}
