package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.form.GeneralFormResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GeneralFormResponseRepository extends JpaRepository<GeneralFormResponse, Long> {
    List<GeneralFormResponse> findAllByFormId(String formId);
    List<GeneralFormResponse> findAllByFormIdAndDeletedAtIsNull(String formId);
    Optional<GeneralFormResponse> findByFormIdAndUserEmail(String formId, String userEmail);
    long countByFormId(String formId);
}
