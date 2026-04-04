package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.TeamCollaboration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TeamCollaborationRepository extends JpaRepository<TeamCollaboration, Long> {
    List<TeamCollaboration> findByCourseId(Long courseId);
    List<TeamCollaboration> findByTeamLeaderId(Long teamLeaderId);
    List<TeamCollaboration> findByIsActiveTrue();
}
