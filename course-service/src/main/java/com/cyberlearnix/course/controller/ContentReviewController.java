package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.ContentReview;
import com.cyberlearnix.shared.repository.course.ContentReviewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/content-reviews")
public class ContentReviewController {

    private final ContentReviewRepository contentReviewRepository;

    public ContentReviewController(ContentReviewRepository contentReviewRepository) {
        this.contentReviewRepository = contentReviewRepository;
    }

    @GetMapping
    public ResponseEntity<List<ContentReview>> getAllReviews(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long teacherId) {
        if (status != null) {
            return ResponseEntity.ok(contentReviewRepository.findByReviewStatus(status));
        }
        if (teacherId != null) {
            return ResponseEntity.ok(contentReviewRepository.findByTeacherId(teacherId));
        }
        return ResponseEntity.ok(contentReviewRepository.findAll());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ContentReview>> getPendingReviews() {
        return ResponseEntity.ok(contentReviewRepository.findByReviewStatus("pending"));
    }

    @PostMapping
    public ResponseEntity<ContentReview> submitForReview(@RequestBody ContentReview review) {
        review.setReviewStatus("pending");
        review.setSubmittedAt(LocalDateTime.now());
        review.setIsApproved(null);
        return ResponseEntity.ok(contentReviewRepository.save(review));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<ContentReview> reviewContent(
            @PathVariable Long id,
            @RequestBody Map<String, Object> reviewData) {
        Optional<ContentReview> existing = contentReviewRepository.findById(id);
        if (existing.isPresent()) {
            ContentReview review = existing.get();
            review.setReviewerId(Long.valueOf(reviewData.get("reviewerId").toString()));
            review.setReviewNotes((String) reviewData.get("notes"));
            review.setIsApproved((Boolean) reviewData.get("approved"));
            review.setRequiresRevision((Boolean) reviewData.getOrDefault("requiresRevision", false));
            if (review.getRequiresRevision()) {
                review.setRevisionNotes((String) reviewData.get("revisionNotes"));
                review.setReviewStatus("revision_required");
            } else if (review.getIsApproved()) {
                review.setReviewStatus("approved");
            } else {
                review.setReviewStatus("rejected");
            }
            review.setReviewedAt(LocalDateTime.now());
            return ResponseEntity.ok(contentReviewRepository.save(review));
        }
        return ResponseEntity.notFound().build();
    }

    @PatchMapping("/{id}/revise")
    public ResponseEntity<ContentReview> submitRevision(@PathVariable Long id, @RequestBody Map<String, String> revisionData) {
        Optional<ContentReview> existing = contentReviewRepository.findById(id);
        if (existing.isPresent()) {
            ContentReview review = existing.get();
            review.setContent(revisionData.get("content"));
            review.setReviewStatus("pending");
            review.setSubmittedAt(LocalDateTime.now());
            review.setIsApproved(null);
            return ResponseEntity.ok(contentReviewRepository.save(review));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable Long id) {
        contentReviewRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
