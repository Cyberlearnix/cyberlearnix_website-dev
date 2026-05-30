package com.cyberlearnix.lab.repository;

import com.cyberlearnix.lab.entity.AssignmentStatus;
import com.cyberlearnix.lab.entity.LabAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface LabAssignmentRepository extends JpaRepository<LabAssignment, Long> {

    List<LabAssignment> findByStudentIdAndStatus(String studentId, AssignmentStatus status);

    List<LabAssignment> findByStatus(AssignmentStatus status);

    List<LabAssignment> findByStatusAndLastActiveAtBefore(AssignmentStatus status, Instant threshold);

    Optional<LabAssignment> findFirstByStudentIdAndStatusIn(String studentId, List<AssignmentStatus> statuses);

    List<LabAssignment> findByStudentIdAndCourseId(String studentId, Long courseId);

    List<LabAssignment> findByCourseId(Long courseId);
}
