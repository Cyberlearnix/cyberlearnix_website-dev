package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.Certificate;
import com.cyberlearnix.shared.entity.course.CertificateTemplate;
import com.cyberlearnix.shared.repository.course.CertificateRepository;
import com.cyberlearnix.shared.repository.course.CertificateTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<List<Certificate>> getAllCertificates(@RequestParam(required = false) String studentId) {
        if (studentId != null) {
            return ResponseEntity.ok(certificateRepository.findByStudentId(studentId));
        }
        return ResponseEntity.ok(certificateRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Certificate> issueCertificate(@RequestBody Certificate certificate) {
        // Generate unique certificate ID if not provided
        if (certificate.getCertificateId() == null || certificate.getCertificateId().isEmpty()) {
            certificate.setCertificateId("CERT-" + System.currentTimeMillis() + "-" + certificate.getStudentId());
        }
        // Set issued date if not provided
        if (certificate.getIssuedAt() == null) {
            certificate.setIssuedAt(java.time.LocalDateTime.now());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(certificateRepository.save(certificate));
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

    /** Public endpoint — no auth required — used by QR verify link on the certificate */
    @GetMapping("/verify/{credentialId}")
    public ResponseEntity<?> verifyCertificate(@PathVariable String credentialId) {
        return certificateRepository.findByCertificateId(credentialId)
                .map(cert -> ResponseEntity.ok(Map.of(
                        "valid", true,
                        "credentialId", cert.getCertificateId(),
                        "studentName", cert.getStudentName() != null ? cert.getStudentName() : "",
                        "courseTitle", cert.getCourseTitle() != null ? cert.getCourseTitle() : "",
                        "instructorName", cert.getInstructorName() != null ? cert.getInstructorName() : "",
                        "issuedAt", cert.getIssuedAt() != null ? cert.getIssuedAt().toString() : ""
                )))
                .orElse(ResponseEntity.ok(Map.of("valid", false)));
    }
}
