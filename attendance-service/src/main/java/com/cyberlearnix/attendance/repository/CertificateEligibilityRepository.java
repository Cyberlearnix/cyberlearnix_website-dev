package com.cyberlearnix.attendance.repository;

import com.cyberlearnix.attendance.entity.CertificateEligibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateEligibilityRepository extends JpaRepository<CertificateEligibility, String> {

    Optional<CertificateEligibility> findByStudentIdAndCourseId(String studentId, String courseId);

    List<CertificateEligibility> findByCourseIdOrderByOverallAttendancePercentageDesc(String courseId);

    List<CertificateEligibility> findByStudentIdOrderByCreatedAtDesc(String studentId);

    List<CertificateEligibility> findByCourseIdAndEligibleTrue(String courseId);

    @Query("SELECT ce FROM CertificateEligibility ce WHERE ce.courseId = :courseId AND ce.eligible = false ORDER BY ce.overallAttendancePercentage ASC")
    List<CertificateEligibility> findIneligibleStudents(@Param("courseId") String courseId);
}
