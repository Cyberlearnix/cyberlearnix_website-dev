package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.entity.SiteSetting;
import com.cyberlearnix.admin.repository.SiteSettingRepository;
import com.cyberlearnix.admin.service.ZohoTokenService;
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
    private final ZohoTokenService zohoTokenService;

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

    /**
     * Exchange a Zoho Self-Client authorization code for tokens and persist the
     * refresh token to the DB.  Accepts {"authCode": "1000.xxx..."}.
     * After this call the in-memory and DB tokens are updated; no pod restart needed.
     */
    @PostMapping("/zoho/exchange-token")
    public ResponseEntity<?> exchangeZohoToken(@RequestBody Map<String, String> body) {
        String authCode = body.get("authCode");
        if (authCode == null || authCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "authCode is required"));
        }
        try {
            Map<String, Object> result = zohoTokenService.exchangeAuthCode(authCode);
            return ResponseEntity.ok(Map.of(
                    "message", "Zoho tokens updated successfully",
                    "scope", result.getOrDefault("scope", ""),
                    "expires_in", result.getOrDefault("expires_in", 3600)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Directly seed a Zoho refresh token (use when you already have a valid refresh_token).
     * Accepts {"refreshToken": "1000.xxx..."}.
     */
    @PostMapping("/zoho/refresh-token")
    public ResponseEntity<?> setZohoRefreshToken(@RequestBody Map<String, String> body) {
        String token = body.get("refreshToken");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "refreshToken is required"));
        }
        zohoTokenService.seedToken(token, null, 0);
        return ResponseEntity.ok(Map.of("message", "Zoho refresh token updated. Next API call will trigger a fresh token fetch."));
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
