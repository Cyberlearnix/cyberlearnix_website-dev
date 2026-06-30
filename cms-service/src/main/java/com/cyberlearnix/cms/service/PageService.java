package com.cyberlearnix.cms.service;

import com.cyberlearnix.cms.dto.PageCreateDTO;
import com.cyberlearnix.shared.entity.cms.Page;
import com.cyberlearnix.shared.entity.cms.PageSection;
import com.cyberlearnix.shared.entity.cms.PageComponent;
import com.cyberlearnix.shared.repository.cms.PageRepository;
import com.cyberlearnix.shared.repository.cms.PageSectionRepository;
import com.cyberlearnix.shared.repository.cms.PageComponentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PageService {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private PageSectionRepository sectionRepository;

    @Autowired
    private PageComponentRepository componentRepository;

    public List<Page> getAllPages() {
        return pageRepository.findAll();
    }

    public Optional<Page> getPageBySlug(String slug) {
        return pageRepository.findBySlug(slug);
    }

    public Optional<Page> getPageById(Long id) {
        return pageRepository.findById(id);
    }

    @Transactional
    public Page createPage(PageCreateDTO dto) {
        Page page = new Page();
        page.setTitle(dto.getTitle());
        page.setSubtitle(dto.getSubtitle());
        page.setSlug(dto.getSlug());
        page.setTemplateName(dto.getTemplateName());
        page.setIsPublished(dto.getIsPublished() != null ? dto.getIsPublished() : false);
        page.setMetaTitle(dto.getMetaTitle());
        page.setMetaDescription(dto.getMetaDescription());
        page.setMetaKeywords(dto.getMetaKeywords());
        page.setCreatedAt(LocalDateTime.now());
        page.setUpdatedAt(LocalDateTime.now());
        
        return pageRepository.save(page);
    }

    @Transactional
    public Page updatePage(Long id, PageCreateDTO dto) {
        return pageRepository.findById(id).map(page -> {
            if (dto.getTitle() != null) page.setTitle(dto.getTitle());
            if (dto.getSubtitle() != null) page.setSubtitle(dto.getSubtitle());
            if (dto.getSlug() != null) page.setSlug(dto.getSlug());
            if (dto.getTemplateName() != null) page.setTemplateName(dto.getTemplateName());
            if (dto.getIsPublished() != null) page.setIsPublished(dto.getIsPublished());
            if (dto.getMetaTitle() != null) page.setMetaTitle(dto.getMetaTitle());
            if (dto.getMetaDescription() != null) page.setMetaDescription(dto.getMetaDescription());
            if (dto.getMetaKeywords() != null) page.setMetaKeywords(dto.getMetaKeywords());
            page.setUpdatedAt(LocalDateTime.now());
            return pageRepository.save(page);
        }).orElseThrow(() -> new RuntimeException("Page not found"));
    }

    @Transactional
    public void deletePage(Long id) {
        pageRepository.deleteById(id);
    }

    @Transactional
    public PageSection addSection(Long pageId, PageSection section) {
        return pageRepository.findById(pageId).map(page -> {
            section.setPage(page);
            if (section.getOrderIndex() == null || section.getOrderIndex() == 0) {
                List<PageSection> existing = sectionRepository.findByPageIdOrderByOrderIndexAsc(pageId);
                section.setOrderIndex(existing.size() + 1);
            }
            return sectionRepository.save(section);
        }).orElseThrow(() -> new RuntimeException("Page not found"));
    }

    @Transactional
    public PageComponent addComponent(Long sectionId, PageComponent component) {
        return sectionRepository.findById(sectionId).map(section -> {
            component.setSection(section);
            if (component.getOrderIndex() == null || component.getOrderIndex() == 0) {
                List<PageComponent> existing = componentRepository.findBySectionIdOrderByOrderIndexAsc(sectionId);
                component.setOrderIndex(existing.size() + 1);
            }
            return componentRepository.save(component);
        }).orElseThrow(() -> new RuntimeException("Section not found"));
    }
}
