package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.MenuItem;
import com.cyberlearnix.shared.repository.MenuItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/menus")
public class MenuController {

    private final MenuItemRepository menuItemRepository;

    public MenuController(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
        initializeDefaultMenus();
    }

    private void initializeDefaultMenus() {
        if (menuItemRepository.count() == 0) {
            menuItemRepository.save(createMenuItem("Home", "/", "header", 1, true, "home", null));
            menuItemRepository.save(createMenuItem("Courses", "/courses", "header", 2, true, "book", null));
            menuItemRepository.save(createMenuItem("About", "/about", "header", 3, true, "info", null));
            menuItemRepository.save(createMenuItem("Contact", "/contact", "header", 4, true, "mail", null));
        }
    }

    private MenuItem createMenuItem(String label, String url, String location, int order, boolean active, String icon, Long parentId) {
        MenuItem item = new MenuItem();
        item.setLabel(label);
        item.setUrl(url);
        item.setLocation(location);
        item.setDisplayOrder(order);
        item.setIsActive(active);
        item.setIcon(icon);
        item.setParentId(parentId);
        return item;
    }

    @GetMapping
    public ResponseEntity<List<MenuItem>> getAllMenus(@RequestParam(required = false) String location) {
        if (location != null) {
            return ResponseEntity.ok(menuItemRepository.findByLocationAndIsActiveTrueOrderByDisplayOrderAsc(location));
        }
        return ResponseEntity.ok(menuItemRepository.findByIsActiveTrueOrderByDisplayOrderAsc());
    }

    @PostMapping
    public ResponseEntity<MenuItem> createMenu(@RequestBody MenuItem menu) {
        menu.setCreatedAt(java.time.LocalDateTime.now());
        menu.setUpdatedAt(java.time.LocalDateTime.now());
        MenuItem saved = menuItemRepository.save(menu);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuItem> updateMenu(@PathVariable Long id, @RequestBody MenuItem updates) {
        Optional<MenuItem> existing = menuItemRepository.findById(id);
        if (existing.isPresent()) {
            MenuItem menu = existing.get();
            menu.setLabel(updates.getLabel());
            menu.setUrl(updates.getUrl());
            menu.setLocation(updates.getLocation());
            menu.setDisplayOrder(updates.getDisplayOrder());
            menu.setIsActive(updates.getIsActive());
            menu.setIcon(updates.getIcon());
            menu.setParentId(updates.getParentId());
            menu.setOpenInNewTab(updates.getOpenInNewTab());
            menu.setCssClass(updates.getCssClass());
            menu.setUpdatedAt(java.time.LocalDateTime.now());
            return ResponseEntity.ok(menuItemRepository.save(menu));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMenu(@PathVariable Long id) {
        menuItemRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<MenuItem> toggleMenu(@PathVariable Long id) {
        Optional<MenuItem> existing = menuItemRepository.findById(id);
        if (existing.isPresent()) {
            MenuItem menu = existing.get();
            menu.setIsActive(!menu.getIsActive());
            menu.setUpdatedAt(java.time.LocalDateTime.now());
            return ResponseEntity.ok(menuItemRepository.save(menu));
        }
        return ResponseEntity.notFound().build();
    }

    @PatchMapping("/{id}/reorder")
    public ResponseEntity<MenuItem> reorderMenu(@PathVariable Long id, @RequestBody Map<String, Integer> order) {
        Optional<MenuItem> existing = menuItemRepository.findById(id);
        if (existing.isPresent()) {
            MenuItem menu = existing.get();
            menu.setDisplayOrder(order.get("order"));
            menu.setUpdatedAt(java.time.LocalDateTime.now());
            return ResponseEntity.ok(menuItemRepository.save(menu));
        }
        return ResponseEntity.notFound().build();
    }
}
