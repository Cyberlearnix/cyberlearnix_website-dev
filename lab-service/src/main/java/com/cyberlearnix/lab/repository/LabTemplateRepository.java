package com.cyberlearnix.lab.repository;

import com.cyberlearnix.lab.entity.LabTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabTemplateRepository extends JpaRepository<LabTemplate, Long> {

    List<LabTemplate> findByIsActiveTrue();
}
