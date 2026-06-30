package com.cyberlearnix.cms.controller;

import com.cyberlearnix.shared.entity.cms.Testimonial;
import com.cyberlearnix.shared.repository.cms.TestimonialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cms/testimonials")
public class TestimonialController {

    @Autowired
    private TestimonialRepository testimonialRepository;

    @GetMapping
    public List<Testimonial> getAllTestimonials() {
        return testimonialRepository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Testimonial> createTestimonial(@RequestBody Testimonial testimonial) {
        return ResponseEntity.status(HttpStatus.CREATED).body(testimonialRepository.save(testimonial));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Testimonial> updateTestimonial(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return testimonialRepository.findById(id).map(testimonial -> {
            if (updates.containsKey("name"))
                testimonial.setName((String) updates.get("name"));
            if (updates.containsKey("role"))
                testimonial.setRole((String) updates.get("role"));
            if (updates.containsKey("feedback"))
                testimonial.setFeedback((String) updates.get("feedback"));
            if (updates.containsKey("imageUrl"))
                testimonial.setImageUrl((String) updates.get("imageUrl"));

            return ResponseEntity.ok(testimonialRepository.save(testimonial));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteTestimonial(@PathVariable Long id) {
        if (testimonialRepository.existsById(id)) {
            testimonialRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
