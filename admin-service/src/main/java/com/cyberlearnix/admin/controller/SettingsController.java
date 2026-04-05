package com.cyberlearnix.admin.controller;

import com.cyberlearnix.shared.entity.user.SiteSetting;
import com.cyberlearnix.shared.repository.SiteSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SiteSettingRepository siteSettingRepository;

    @GetMapping
    public List<SiteSetting> getAllSettings(@RequestParam(required = false) String group) {
        if (group != null) {
            return siteSettingRepository.findAll().stream()
                    .filter(s -> s.getSettingGroup().equalsIgnoreCase(group))
                    .toList();
        }
        return siteSettingRepository.findAll();
    }

    @PutMapping("/platform")
    public ResponseEntity<?> updatePlatformSettings(@RequestBody Map<String, String> settings) {
        return updateSettingsGroup("PLATFORM", settings);
    }

    @PutMapping("/payment")
    public ResponseEntity<?> updatePaymentSettings(@RequestBody Map<String, String> settings) {
        return updateSettingsGroup("PAYMENT", settings);
    }

    @PutMapping("/notifications")
    public ResponseEntity<?> updateNotificationSettings(@RequestBody Map<String, String> settings) {
        return updateSettingsGroup("NOTIFICATION", settings);
    }

    private ResponseEntity<?> updateSettingsGroup(String group, Map<String, String> settings) {
        settings.forEach((key, value) -> {
            Optional<SiteSetting> existing = siteSettingRepository.findAll().stream()
                    .filter(s -> s.getSettingKey().equals(key))
                    .findFirst();
            
            SiteSetting setting = existing.orElse(new SiteSetting());
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setSettingGroup(group);
            setting.setUpdatedAt(LocalDateTime.now());
            siteSettingRepository.save(setting);
        });
        return ResponseEntity.ok(Map.of("message", group + " settings updated successfully"));
    }
}
