package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.course.ModuleContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModuleContentRepository extends JpaRepository<ModuleContent, Long> {

    List<ModuleContent> findByModuleIdOrderByOrderIndex(Long moduleId);

    Optional<ModuleContent> findByIdAndModuleId(Long id, Long moduleId);

    List<ModuleContent> findByModuleIdAndIsActiveOrderByOrderIndex(Long moduleId, Boolean isActive);

    @Query("SELECT COUNT(c) FROM ModuleContent c WHERE c.module.id = :moduleId")
    Long countByModuleId(@Param("moduleId") Long moduleId);

    @Query("SELECT MAX(c.orderIndex) FROM ModuleContent c WHERE c.module.id = :moduleId")
    Integer findMaxOrderIndexByModuleId(@Param("moduleId") Long moduleId);

    @Query("SELECT c FROM ModuleContent c WHERE c.module.id = :moduleId AND c.contentType = :type")
    List<ModuleContent> findByModuleIdAndContentType(Long moduleId, String type);

    @Query("SELECT COUNT(c) FROM ModuleContent c WHERE c.module.course.id = :courseId AND c.isActive = true")
    Long countByCourseId(@Param("courseId") Long courseId);
}
