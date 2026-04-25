package com.cyberlearnix.admin.repository;

import com.cyberlearnix.admin.entity.TeamsMeeting;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration / JPA tests for {@link TeamsMeetingRepository}.
 *
 * Uses a full Spring Boot context with the "test" profile (H2 in PostgreSQL mode).
 * Dummy Zoho properties in application-test.properties satisfy the @Value fields;
 * TeamsService.initOrgId() skips HTTP discovery because zoho.org-id != "0".
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class TeamsMeetingRepositoryIT {

    @Autowired
    private TeamsMeetingRepository repository;

    private TeamsMeeting buildMeeting(String subject, String graphId) {
        TeamsMeeting m = new TeamsMeeting();
        m.setSubject(subject);
        m.setGraphMeetingId(graphId);
        m.setOrganizerUserId("organizer-001");
        m.setRecurring(false);
        return m;
    }

    @Test
    void save_andFindById_roundTrip() {
        TeamsMeeting saved = repository.save(buildMeeting("Sprint Planning", "graph-abc-123"));

        Optional<TeamsMeeting> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getSubject()).isEqualTo("Sprint Planning");
    }

    @Test
    void findAll_returnsAllSavedMeetings() {
        repository.save(buildMeeting("Daily Standup", "graph-001"));
        repository.save(buildMeeting("Retro", "graph-002"));

        List<TeamsMeeting> all = repository.findAll();

        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void save_persistsGraphMeetingId() {
        TeamsMeeting saved = repository.save(buildMeeting("Kick-off", "graph-unique-xyz"));

        assertThat(saved.getGraphMeetingId()).isEqualTo("graph-unique-xyz");
    }
}
