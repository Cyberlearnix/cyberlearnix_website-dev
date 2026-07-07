package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.entity.SiteSetting;
import com.cyberlearnix.admin.repository.SiteSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SiteSettingRepository siteSettingRepository;

    @GetMapping
    public List<SiteSetting> getAllSettings(@RequestParam(required = false) String group) {
        if (group != null) {
            return siteSettingRepository.findBySettingGroup(group);
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
            SiteSetting setting = siteSettingRepository.findBySettingKey(key)
                    .orElse(new SiteSetting());
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setSettingGroup(group);
            siteSettingRepository.save(setting);
        });
        return ResponseEntity.ok(Map.of("message", group + " settings updated successfully"));
    }
}
