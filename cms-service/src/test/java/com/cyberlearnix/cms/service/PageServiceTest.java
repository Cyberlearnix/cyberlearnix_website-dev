package com.cyberlearnix.cms.service;

import com.cyberlearnix.cms.dto.PageCreateDTO;
import com.cyberlearnix.shared.entity.cms.Page;
import com.cyberlearnix.shared.repository.cms.PageComponentRepository;
import com.cyberlearnix.shared.repository.cms.PageRepository;
import com.cyberlearnix.shared.repository.cms.PageSectionRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PageService}.
 *
 * All JPA repositories are mocked so no Spring context or database is required.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PageServiceTest {

    @Mock private PageRepository pageRepository;
    @Mock private PageSectionRepository sectionRepository;
    @Mock private PageComponentRepository componentRepository;

    @InjectMocks
    private PageService pageService;

    // ── getAllPages ───────────────────────────────────────────────────────────────

    @Test
    void getAllPages_returnsEmptyList_whenNoPages() {
        when(pageRepository.findAll()).thenReturn(List.of());

        assertThat(pageService.getAllPages()).isEmpty();
    }

    @Test
    void getAllPages_returnsAllPagesFromRepository() {
        Page p = new Page();
        p.setTitle("Home");
        when(pageRepository.findAll()).thenReturn(List.of(p));

        List<Page> result = pageService.getAllPages();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Home");
    }

    // ── getPageBySlug ─────────────────────────────────────────────────────────────

    @Test
    void getPageBySlug_returnsEmpty_whenSlugNotFound() {
        when(pageRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThat(pageService.getPageBySlug("missing")).isEmpty();
    }

    @Test
    void getPageBySlug_returnsPage_whenSlugExists() {
        Page page = new Page();
        page.setSlug("about");
        when(pageRepository.findBySlug("about")).thenReturn(Optional.of(page));

        Optional<Page> result = pageService.getPageBySlug("about");

        assertThat(result).isPresent();
        assertThat(result.get().getSlug()).isEqualTo("about");
    }

    // ── createPage ────────────────────────────────────────────────────────────────

    @Test
    void createPage_savesAndReturnsPage() {
        PageCreateDTO dto = new PageCreateDTO();
        dto.setTitle("Contact");
        dto.setSlug("contact");

        Page saved = new Page();
        saved.setTitle("Contact");
        saved.setSlug("contact");
        when(pageRepository.save(any(Page.class))).thenReturn(saved);

        Page result = pageService.createPage(dto);

        assertThat(result.getTitle()).isEqualTo("Contact");
        verify(pageRepository).save(any(Page.class));
    }

    // ── updatePage ────────────────────────────────────────────────────────────────

    @Test
    void updatePage_throws_whenPageNotFound() {
        when(pageRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pageService.updatePage(99L, new PageCreateDTO()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }
}
