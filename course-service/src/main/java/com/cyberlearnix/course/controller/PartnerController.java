package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.ContentPartner;
import com.cyberlearnix.shared.repository.course.ContentPartnerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/partners")
public class PartnerController {

    @Autowired
    private ContentPartnerRepository partnerRepository;

    @GetMapping
    public ResponseEntity<?> getPartners() {
        return ResponseEntity.ok(Map.of("success", true, "partners", partnerRepository.findAll()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createPartner(@RequestBody ContentPartner partner) {
        partner.setCreatedAt(LocalDateTime.now());
        ContentPartner saved = partnerRepository.save(partner);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "partner", saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updatePartner(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return partnerRepository.findById(id).map(partner -> {
            if (updates.containsKey("name"))
                partner.setName((String) updates.get("name"));
            if (updates.containsKey("url"))
                partner.setUrl((String) updates.get("url"));
            if (updates.containsKey("logo_url"))
                partner.setLogoUrl((String) updates.get("logo_url"));

            return ResponseEntity.ok(Map.of("success", true, "partner", partnerRepository.save(partner)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deletePartner(@PathVariable Long id) {
        if (partnerRepository.existsById(id)) {
            partnerRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.notFound().build();
    }
}
