package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.AssignmentContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AssignmentContentRepository extends JpaRepository<AssignmentContent, Long> {
    
    List<AssignmentContent> findByModuleIdOrderByOrderIndex(Long moduleId);
    
    @Query("SELECT a FROM AssignmentContent a WHERE a.module.id = :moduleId AND a.assignmentType = :type")
    List<AssignmentContent> findByModuleIdAndAssignmentType(Long moduleId, String type);
    
    @Query("SELECT a FROM AssignmentContent a WHERE a.dueDate > :now ORDER BY a.dueDate")
    List<AssignmentContent> findUpcomingAssignments(@Param("now") LocalDateTime now);
    
    @Query("SELECT a FROM AssignmentContent a WHERE a.dueDate < :now AND a.dueDate > :past ORDER BY a.dueDate DESC")
    List<AssignmentContent> findRecentAssignments(@Param("now") LocalDateTime now, @Param("past") LocalDateTime past);
}
