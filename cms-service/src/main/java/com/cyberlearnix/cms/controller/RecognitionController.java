package com.cyberlearnix.cms.controller;

import com.cyberlearnix.shared.entity.cms.Recognition;
import com.cyberlearnix.shared.repository.cms.RecognitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cms/recognitions")
public class RecognitionController {

    @Autowired
    private RecognitionRepository recognitionRepository;

    @GetMapping
    public List<Recognition> getAllRecognitions() {
        return recognitionRepository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Recognition> createRecognition(@RequestBody Recognition recognition) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recognitionRepository.save(recognition));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Recognition> updateRecognition(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return recognitionRepository.findById(id).map(recognition -> {
            if (updates.containsKey("title"))
                recognition.setTitle((String) updates.get("title"));
            if (updates.containsKey("description"))
                recognition.setDescription((String) updates.get("description"));
            if (updates.containsKey("authority"))
                recognition.setAuthority((String) updates.get("authority"));
            if (updates.containsKey("certificateNo"))
                recognition.setCertificateNo((String) updates.get("certificateNo"));
            if (updates.containsKey("validUntil"))
                recognition.setValidUntil((String) updates.get("validUntil"));
            if (updates.containsKey("logoUrl"))
                recognition.setLogoUrl((String) updates.get("logoUrl"));
            if (updates.containsKey("verifyUrl"))
                recognition.setVerifyUrl((String) updates.get("verifyUrl"));

            return ResponseEntity.ok(recognitionRepository.save(recognition));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteRecognition(@PathVariable Long id) {
        if (recognitionRepository.existsById(id)) {
            recognitionRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
