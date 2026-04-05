package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.enrollment.EnrollmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EnrollmentSubmissionRepository extends JpaRepository<EnrollmentSubmission, Long> {
    List<EnrollmentSubmission> findByStudentEmail(String studentEmail);

    List<EnrollmentSubmission> findByDeletedAtIsNull();

    List<EnrollmentSubmission> findByDeletedAtIsNotNull();

    boolean existsByCourseIdAndStudentEmailAndDeletedAtIsNull(Long courseId, String studentEmail);
}
