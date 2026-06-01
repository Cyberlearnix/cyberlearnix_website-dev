package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.MenuItem;
import com.cyberlearnix.shared.repository.user.MenuItemRepository;
import org.springframework.http.HttpStatus;
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
            // Top-level nav items
            MenuItem home     = menuItemRepository.save(createMenuItem("Home",       "/",        "header", 1, true,  "home",       null,  null));
            MenuItem services = menuItemRepository.save(createMenuItem("Services",   "/services", "header", 2, true,  "briefcase",  null,  null));
            MenuItem courses  = menuItemRepository.save(createMenuItem("Courses",    "/courses",  "header", 3, true,  "book",       null,  null));
            menuItemRepository.save(createMenuItem("Blog",       "/blog",     "header", 4, true,  "file-text",  null,  null));
            menuItemRepository.save(createMenuItem("Careers",    "/careers",  "header", 5, true,  "users",      null,  null));
            menuItemRepository.save(createMenuItem("Login",      "/login",    "header", 6, true,  "user",       null,  null));
            menuItemRepository.save(createMenuItem("Contact Us", "/contact",  "header", 7, true,  "mail",       null,  "btn btn-primary nav-btn"));

            // Services dropdown children
            menuItemRepository.save(createMenuItem("Cybersecurity Services",     "/cyber-services",           "header", 1, true, null, services.getId(), null));
            menuItemRepository.save(createMenuItem("Training & Certifications",  "/training-certifications",  "header", 2, true, null, services.getId(), null));
            menuItemRepository.save(createMenuItem("Web App Development",        "/web-dev",                  "header", 3, true, null, services.getId(), null));
            menuItemRepository.save(createMenuItem("Mobile App Development",     "/mobile-dev",               "header", 4, true, null, services.getId(), null));

            // Courses dropdown children
            menuItemRepository.save(createMenuItem("Cybersecurity Essentials", "/cyber-essentials", "header", 1, true, null, courses.getId(), null));
            menuItemRepository.save(createMenuItem("Ethical Hacking",          "/ethical-hacking",  "header", 2, true, null, courses.getId(), null));
            menuItemRepository.save(createMenuItem("SOC Analyst Program",      "/soc-analyst",      "header", 3, true, null, courses.getId(), null));
        }
    }

    private MenuItem createMenuItem(String label, String url, String location, int order, boolean active, String icon, Long parentId, String cssClass) {
        MenuItem item = new MenuItem();
        item.setLabel(label);
        item.setUrl(url);
        item.setLocation(location);
        item.setDisplayOrder(order);
        item.setIsActive(active);
        item.setIcon(icon);
        item.setParentId(parentId);
        item.setCssClass(cssClass);
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
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
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
