package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.JobOpening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobOpeningRepository extends JpaRepository<JobOpening, UUID> {
    List<JobOpening> findByStatus(String status);
    List<JobOpening> findByType(String type);
    List<JobOpening> findByStatusAndType(String status, String type);
}
