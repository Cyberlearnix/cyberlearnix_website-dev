package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.LabContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabContentRepository extends JpaRepository<LabContent, Long> {
    
    List<LabContent> findByModuleIdOrderByOrderIndex(Long moduleId);
    
    @Query("SELECT l FROM LabContent l WHERE l.module.id = :moduleId AND l.difficultyLevel = :level")
    List<LabContent> findByModuleIdAndDifficultyLevel(Long moduleId, String level);
    
    @Query("SELECT l FROM LabContent l WHERE l.module.id = :moduleId AND l.labType = :type")
    List<LabContent> findByModuleIdAndLabType(Long moduleId, String type);
}
