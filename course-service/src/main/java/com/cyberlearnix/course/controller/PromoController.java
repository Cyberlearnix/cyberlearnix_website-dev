package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.PromoBanner;
import com.cyberlearnix.shared.repository.PromoBannerRepository;
import com.cyberlearnix.course.dto.PromoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/promos")
public class PromoController {

    @Autowired
    private PromoBannerRepository promoRepository;

    @GetMapping
    public ResponseEntity<List<PromoBanner>> getAllPromos() {
        return ResponseEntity.ok(promoRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> createPromo(@RequestBody PromoDTO promoDTO,
                                        @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins can manage promos"));
        }

        PromoBanner promo = new PromoBanner();
        promo.setTitle(promoDTO.getTitle());
        promo.setDescription(promoDTO.getDescription());
        promo.setImgUrl(promoDTO.getImgUrl());
        promo.setLink(promoDTO.getLink());
        promo.setStatus(promoDTO.getStatus() != null ? promoDTO.getStatus() : "active");
        promo.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CREATED).body(promoRepository.save(promo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePromo(@PathVariable Long id, 
                                        @RequestBody PromoDTO promoDTO,
                                        @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins can manage promos"));
        }

        return promoRepository.findById(id).map(promo -> {
            if (promoDTO.getTitle() != null) promo.setTitle(promoDTO.getTitle());
            if (promoDTO.getDescription() != null) promo.setDescription(promoDTO.getDescription());
            if (promoDTO.getImgUrl() != null) promo.setImgUrl(promoDTO.getImgUrl());
            if (promoDTO.getLink() != null) promo.setLink(promoDTO.getLink());
            if (promoDTO.getStatus() != null) promo.setStatus(promoDTO.getStatus());
            
            return ResponseEntity.ok(promoRepository.save(promo));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePromo(@PathVariable Long id,
                                        @RequestHeader("X-User-Role") String userRole) {
        if (!"admin".equalsIgnoreCase(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins can manage promos"));
        }

        if (promoRepository.existsById(id)) {
            promoRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.notFound().build();
    }
}
