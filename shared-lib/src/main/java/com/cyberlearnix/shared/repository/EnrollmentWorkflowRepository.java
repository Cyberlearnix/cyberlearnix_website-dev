package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.enrollment.EnrollmentWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentWorkflowRepository extends JpaRepository<EnrollmentWorkflow, Long> {
    Optional<EnrollmentWorkflow> findByIsDefaultTrue();
    List<EnrollmentWorkflow> findByCourseId(Long courseId);
    List<EnrollmentWorkflow> findByIsActiveTrue();
}
