package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByCertificateId(String certificateId);
    List<Certificate> findByStudentId(String studentId);
    Optional<Certificate> findByStudentIdAndCourseId(String studentId, Long courseId);
}
