package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.form.EnrollmentFormConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentFormConfigRepository extends JpaRepository<EnrollmentFormConfig, String> {
    Optional<EnrollmentFormConfig> findByIdAndToken(String id, String token);

    List<EnrollmentFormConfig> findByDeletedAtIsNull();

    List<EnrollmentFormConfig> findByDeletedAtIsNotNull();
}
