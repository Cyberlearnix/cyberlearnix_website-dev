package com.cyberlearnix.lab.repository;

import com.cyberlearnix.lab.entity.ApprovalStatus;
import com.cyberlearnix.lab.entity.LabApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabApprovalRequestRepository extends JpaRepository<LabApprovalRequest, Long> {

    List<LabApprovalRequest> findByStatus(ApprovalStatus status);

    List<LabApprovalRequest> findByCourseIdAndStudentId(Long courseId, String studentId);

    List<LabApprovalRequest> findByStudentId(String studentId);

    List<LabApprovalRequest> findByCourseId(Long courseId);
}
