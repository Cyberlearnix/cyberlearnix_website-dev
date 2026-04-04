package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.GeneralForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GeneralFormRepository extends JpaRepository<GeneralForm, String> {
    Optional<GeneralForm> findByIdAndToken(String id, String token);
    List<GeneralForm> findAllByDeletedAtIsNull();
    List<GeneralForm> findAllByDeletedAtIsNotNull();
    Optional<GeneralForm> findByIdAndDeletedAtIsNull(String id);
}
