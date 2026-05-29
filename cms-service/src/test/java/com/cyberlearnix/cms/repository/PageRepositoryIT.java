package com.cyberlearnix.cms.repository;

import com.cyberlearnix.shared.entity.cms.Page;
import com.cyberlearnix.shared.repository.cms.PageRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration / JPA tests for {@link PageRepository}.
 *
 * Uses a full Spring Boot context with the "test" profile so H2 (MODE=PostgreSQL)
 * is used. JSON(B) columns (metaKeywords) are stored as text by H2.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class PageRepositoryIT {

    @Autowired
    private PageRepository pageRepository;

    private Page buildPage(String title, String slug) {
        Page page = new Page();
        page.setTitle(title);
        page.setSlug(slug);
        page.setIsPublished(false);
        return pageRepository.save(page);
    }

    @Test
    void findBySlug_returnsPage_whenSlugExists() {
        buildPage("About Us", "about-us");

        Optional<Page> result = pageRepository.findBySlug("about-us");

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("About Us");
    }

    @Test
    void findBySlug_returnsEmpty_whenSlugDoesNotExist() {
        Optional<Page> result = pageRepository.findBySlug("does-not-exist");

        assertThat(result).isEmpty();
    }

    @Test
    void existsBySlug_returnsTrue_whenSlugExists() {
        buildPage("Privacy Policy", "privacy-policy");

        assertThat(pageRepository.existsBySlug("privacy-policy")).isTrue();
    }

    @Test
    void existsBySlug_returnsFalse_whenSlugAbsent() {
        assertThat(pageRepository.existsBySlug("no-such-page")).isFalse();
    }
}
