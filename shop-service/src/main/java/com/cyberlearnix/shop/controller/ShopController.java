package com.cyberlearnix.shop.controller;

import com.cyberlearnix.shared.entity.shop.ShopSettings;
import com.cyberlearnix.shared.repository.ShopSettingsRepository;
import com.cyberlearnix.shop.dto.ShopSettingsDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/shop")
public class ShopController {

    @Autowired
    private ShopSettingsRepository shopSettingsRepository;

    @GetMapping
    public ResponseEntity<?> getShopSettings() {
        return shopSettingsRepository.findFirstByOrderByIdAsc()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok(Map.of("enabled", false)));
    }

    @PutMapping
    public ResponseEntity<?> updateShopSettings(@RequestBody ShopSettingsDTO settingsDTO) {
        // Auth check for admin role should be implemented here or in a Filter
        return shopSettingsRepository.findFirstByOrderByIdAsc().map(existing -> {
            if (settingsDTO.getEnabled() != null) existing.setEnabled(settingsDTO.getEnabled());
            if (settingsDTO.getShopUrl() != null) existing.setShopUrl(settingsDTO.getShopUrl());
            if (settingsDTO.getAnnouncementText() != null) existing.setAnnouncementText(settingsDTO.getAnnouncementText());
            if (settingsDTO.getName() != null) existing.setName(settingsDTO.getName());
            if (settingsDTO.getDescription() != null) existing.setDescription(settingsDTO.getDescription());
            if (settingsDTO.getCurrency() != null) existing.setCurrency(settingsDTO.getCurrency());
            if (settingsDTO.getTheme() != null) existing.setTheme(settingsDTO.getTheme());
            
            ShopSettings saved = shopSettingsRepository.save(existing);
            return ResponseEntity.ok(Map.of("success", true, "data", saved));
        }).orElse(ResponseEntity.notFound().build());
    }
}
