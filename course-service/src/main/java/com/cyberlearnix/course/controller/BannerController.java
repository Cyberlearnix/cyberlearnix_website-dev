package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.Banner;
import com.cyberlearnix.shared.repository.BannerRepository;
import com.cyberlearnix.course.dto.BannerDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/banners")
public class BannerController {

    @Autowired
    private BannerRepository bannerRepository;

    @GetMapping
    public ResponseEntity<List<Banner>> getAllBanners() {
        return ResponseEntity.ok(bannerRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> createBanner(@RequestBody BannerDTO bannerDTO,
                                        @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins can manage banners"));
        }

        Banner banner = new Banner();
        banner.setTitle(bannerDTO.getTitle());
        banner.setSubtitle(bannerDTO.getSubtitle());
        banner.setImgUrl(bannerDTO.getImgUrl());
        banner.setButtons(bannerDTO.getButtons());
        banner.setDisplayOrder(bannerDTO.getDisplayOrder() != null ? bannerDTO.getDisplayOrder() : 0);
        banner.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(bannerRepository.save(banner));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBanner(@PathVariable Long id, 
                                        @RequestBody BannerDTO bannerDTO,
                                        @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins can manage banners"));
        }

        return bannerRepository.findById(id).map(banner -> {
            if (bannerDTO.getTitle() != null) banner.setTitle(bannerDTO.getTitle());
            if (bannerDTO.getSubtitle() != null) banner.setSubtitle(bannerDTO.getSubtitle());
            if (bannerDTO.getImgUrl() != null) banner.setImgUrl(bannerDTO.getImgUrl());
            if (bannerDTO.getButtons() != null) banner.setButtons(bannerDTO.getButtons());
            if (bannerDTO.getDisplayOrder() != null) banner.setDisplayOrder(bannerDTO.getDisplayOrder());
            
            return ResponseEntity.ok(bannerRepository.save(banner));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBanner(@PathVariable Long id,
                                        @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins can manage banners"));
        }

        if (bannerRepository.existsById(id)) {
            bannerRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.notFound().build();
    }
}
