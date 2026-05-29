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

    List<LabAssignment> findByStudentIdAndStatus(Long studentId, AssignmentStatus status);

    List<LabAssignment> findByStatus(AssignmentStatus status);

    List<LabAssignment> findByStatusAndLastActiveAtBefore(AssignmentStatus status, Instant threshold);

    Optional<LabAssignment> findFirstByStudentIdAndStatusIn(Long studentId, List<AssignmentStatus> statuses);
}
