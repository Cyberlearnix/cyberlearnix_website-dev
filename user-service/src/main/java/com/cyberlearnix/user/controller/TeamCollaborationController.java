package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.TeamCollaboration;
import com.cyberlearnix.shared.repository.TeamCollaborationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/teams")
public class TeamCollaborationController {

    private final TeamCollaborationRepository teamRepository;

    public TeamCollaborationController(TeamCollaborationRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @GetMapping
    public ResponseEntity<List<TeamCollaboration>> getAllTeams(@RequestParam(required = false) Long courseId) {
        if (courseId != null) {
            return ResponseEntity.ok(teamRepository.findByCourseId(courseId));
        }
        return ResponseEntity.ok(teamRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamCollaboration> getTeam(@PathVariable Long id) {
        return teamRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TeamCollaboration> createTeam(@RequestBody TeamCollaboration team) {
        team.setCreatedAt(LocalDateTime.now());
        team.setUpdatedAt(LocalDateTime.now());
        if (team.getIsPrivate() && team.getAccessCode() == null) {
            team.setAccessCode(generateAccessCode());
        }
        return ResponseEntity.ok(teamRepository.save(team));
    }

    private String generateAccessCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamCollaboration> updateTeam(@PathVariable Long id, @RequestBody TeamCollaboration updates) {
        Optional<TeamCollaboration> existing = teamRepository.findById(id);
        if (existing.isPresent()) {
            TeamCollaboration team = existing.get();
            team.setName(updates.getName());
            team.setDescription(updates.getDescription());
            team.setTeamLeaderId(updates.getTeamLeaderId());
            team.setMemberIds(updates.getMemberIds());
            team.setCourseId(updates.getCourseId());
            team.setMaxMembers(updates.getMaxMembers());
            team.setIsPrivate(updates.getIsPrivate());
            team.setSharedResources(updates.getSharedResources());
            team.setIsActive(updates.getIsActive());
            team.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(teamRepository.save(team));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTeam(@PathVariable Long id) {
        teamRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<TeamCollaboration> addMember(@PathVariable Long id, @RequestBody Map<String, Long> memberData) {
        Optional<TeamCollaboration> existing = teamRepository.findById(id);
        if (existing.isPresent()) {
            TeamCollaboration team = existing.get();
            Map<String, Object> members = team.getMemberIds();
            if (members == null) {
                members = new HashMap<>();
            }
            members.put(memberData.get("userId").toString(), memberData.get("role"));
            team.setMemberIds(members);
            team.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(teamRepository.save(team));
        }
        return ResponseEntity.notFound().build();
    }
}
