package com.cyberlearnix.shared.repository.identity;

import com.cyberlearnix.shared.entity.identity.IdentityEnrollmentResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IdentityEnrollmentResponseRepository extends JpaRepository<IdentityEnrollmentResponse, String> {
    List<IdentityEnrollmentResponse> findByStatus(String status);
    List<IdentityEnrollmentResponse> findByFormId(String formId);
    long countByStatus(String status);
}
