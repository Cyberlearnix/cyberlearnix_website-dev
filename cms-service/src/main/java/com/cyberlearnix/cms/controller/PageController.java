package com.cyberlearnix.cms.controller;

import com.cyberlearnix.cms.dto.PageCreateDTO;
import com.cyberlearnix.cms.service.PageService;
import com.cyberlearnix.shared.entity.cms.Page;
import com.cyberlearnix.shared.entity.cms.PageSection;
import com.cyberlearnix.shared.entity.cms.PageComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cms")
public class PageController {

    @Autowired
    private PageService pageService;

    @GetMapping("/pages")
    public List<Page> getAllPages() {
        return pageService.getAllPages();
    }

    @GetMapping("/pages/{slug}")
    public ResponseEntity<Page> getPageBySlug(@PathVariable String slug) {
        return pageService.getPageBySlug(slug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/pages")
    public ResponseEntity<Page> createPage(@RequestBody PageCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pageService.createPage(dto));
    }

    @PutMapping("/pages/{id}")
    public ResponseEntity<Page> updatePage(@PathVariable Long id, @RequestBody PageCreateDTO dto) {
        try {
            return ResponseEntity.ok(pageService.updatePage(id, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/pages/{id}")
    public ResponseEntity<Void> deletePage(@PathVariable Long id) {
        pageService.deletePage(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pages/{pageId}/sections")
    public ResponseEntity<PageSection> addSection(@PathVariable Long pageId, @RequestBody PageSection section) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(pageService.addSection(pageId, section));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/sections/{sectionId}/components")
    public ResponseEntity<PageComponent> addComponent(@PathVariable Long sectionId, @RequestBody PageComponent component) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(pageService.addComponent(sectionId, component));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
