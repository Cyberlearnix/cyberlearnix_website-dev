package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.enrollment.Certificate;
import com.cyberlearnix.shared.entity.enrollment.CertificateTemplate;
import com.cyberlearnix.shared.repository.CertificateRepository;
import com.cyberlearnix.shared.repository.CertificateTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateTemplateRepository templateRepository;

    @GetMapping
    public ResponseEntity<List<Certificate>> getAllCertificates() {
        return ResponseEntity.ok(certificateRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Certificate> issueCertificate(@RequestBody Certificate certificate) {
        return ResponseEntity.ok(certificateRepository.save(certificate));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCertificate(@PathVariable Long id) {
        if (certificateRepository.existsById(id)) {
            certificateRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/templates")
    public ResponseEntity<List<CertificateTemplate>> getAllTemplates() {
        return ResponseEntity.ok(templateRepository.findAll());
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<CertificateTemplate> updateTemplate(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        return templateRepository.findById(id).map(template -> {
            if (payload.containsKey("backgroundUrl")) {
                template.setBackgroundUrl(payload.get("backgroundUrl"));
            }
            return ResponseEntity.ok(templateRepository.save(template));
        }).orElse(ResponseEntity.notFound().build());
    }
}
