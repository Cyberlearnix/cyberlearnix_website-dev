package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.SiteSetting;
import com.cyberlearnix.shared.repository.SiteSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/site-settings")
public class SiteSettingsController {

    @Autowired
    private SiteSettingRepository siteSettingRepository;

    @GetMapping
    public ResponseEntity<List<SiteSetting>> getAllSettings(@RequestParam(required = false) String group) {
        if (group != null) {
            return ResponseEntity.ok(siteSettingRepository.findBySettingGroup(group));
        }
        return ResponseEntity.ok(siteSettingRepository.findAll());
    }

    @GetMapping("/{key}")
    public ResponseEntity<SiteSetting> getSettingByKey(@PathVariable String key) {
        return siteSettingRepository.findBySettingKey(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createOrUpdateSetting(@RequestBody SiteSetting setting, 
                                                 @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins can manage site settings"));
        }

        Optional<SiteSetting> existing = siteSettingRepository.findBySettingKey(setting.getSettingKey());
        if (existing.isPresent()) {
            SiteSetting s = existing.get();
            s.setSettingValue(setting.getSettingValue());
            s.setMetadata(setting.getMetadata());
            s.setSettingGroup(setting.getSettingGroup());
            s.setIsActive(setting.getIsActive());
            s.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(siteSettingRepository.save(s));
        }

        setting.setCreatedAt(LocalDateTime.now());
        setting.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(siteSettingRepository.save(setting));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<?> deleteSetting(@PathVariable String key, 
                                         @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins can manage site settings"));
        }

        return siteSettingRepository.findBySettingKey(key).map(s -> {
            siteSettingRepository.delete(s);
            return ResponseEntity.ok(Map.of("success", true));
        }).orElse(ResponseEntity.notFound().build());
    }
}
