package com.cyberlearnix.enrollment.repository;

import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration / JPA slice tests for {@link EnrollmentFormConfigRepository}.
 *
 * Uses a full Spring Boot context with the "test" profile so the H2
 * in-memory database (MODE=PostgreSQL) is used instead of the real PostgreSQL.
 * JSON(B) columns are stored as text by H2 in PostgreSQL compatibility mode.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class EnrollmentFormConfigRepositoryIT {

    @Autowired
    private EnrollmentFormConfigRepository repository;

    private EnrollmentFormConfig buildConfig(String id, String token, LocalDateTime deletedAt) {
        EnrollmentFormConfig cfg = new EnrollmentFormConfig();
        cfg.setId(id);
        cfg.setTitle("Test Form " + id);
        cfg.setToken(token);
        cfg.setFields("[]");
        cfg.setActive(true);
        cfg.setDeletedAt(deletedAt);
        return cfg;
    }

    @Test
    void findByDeletedAtIsNull_returnsOnlyActiveConfigs() {
        repository.save(buildConfig(UUID.randomUUID().toString(), "tok-a", null));
        repository.save(buildConfig(UUID.randomUUID().toString(), "tok-b", LocalDateTime.now()));

        List<EnrollmentFormConfig> active = repository.findByDeletedAtIsNull();

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getToken()).isEqualTo("tok-a");
    }

    @Test
    void findByDeletedAtIsNotNull_returnsTrashedConfigs() {
        repository.save(buildConfig(UUID.randomUUID().toString(), "tok-live", null));
        repository.save(buildConfig(UUID.randomUUID().toString(), "tok-trashed", LocalDateTime.now()));

        List<EnrollmentFormConfig> trashed = repository.findByDeletedAtIsNotNull();

        assertThat(trashed).hasSize(1);
        assertThat(trashed.get(0).getToken()).isEqualTo("tok-trashed");
    }

    @Test
    void findByIdAndToken_returnsConfig_whenBothMatch() {
        String id = UUID.randomUUID().toString();
        repository.save(buildConfig(id, "secret-token", null));

        Optional<EnrollmentFormConfig> result = repository.findByIdAndToken(id, "secret-token");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    void findByIdAndToken_returnsEmpty_whenTokenMismatches() {
        String id = UUID.randomUUID().toString();
        repository.save(buildConfig(id, "real-token", null));

        Optional<EnrollmentFormConfig> result = repository.findByIdAndToken(id, "wrong-token");

        assertThat(result).isEmpty();
    }
}
