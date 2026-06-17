package com.cyberlearnix.shared.repository.identity;

import com.cyberlearnix.shared.entity.identity.IdentityEnrollmentForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IdentityEnrollmentFormRepository extends JpaRepository<IdentityEnrollmentForm, String> {
    List<IdentityEnrollmentForm> findByStatus(String status);
}
