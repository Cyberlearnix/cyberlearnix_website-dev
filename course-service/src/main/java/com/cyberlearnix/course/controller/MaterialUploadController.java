package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.service.CloudinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Handles all media uploads (images, videos, documents) to Cloudinary.
 * All uploaded content is stored on Cloudinary CDN; only the secure_url
 * is returned and saved in the database.
 *
 * Upload Flow:
 *   1. Frontend sends multipart/form-data POST to /api/materials/upload
 *   2. Backend uploads to Cloudinary, gets secure_url
 *   3. Frontend uses that URL when saving course/lecture/profile data
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialUploadController {

    @Autowired
    private CloudinaryService cloudinaryService;

    /**
     * Upload a course thumbnail image.
     * POST /api/materials/upload/thumbnail
     * Header: Authorization: Bearer <token>
     * Body: multipart/form-data { file: <image file> }
     * Returns: { "success": true, "url": "https://res.cloudinary.com/..." }
     */
    @PostMapping(value = "/upload/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadThumbnail(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }

        // Validate type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed for thumbnails"));
        }

        // Max 5MB
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Thumbnail must be under 5MB"));
        }

        try {
            String url = cloudinaryService.uploadImage(file, "cyberlearnix/thumbnails");
            return ResponseEntity.ok(Map.of("success", true, "url", url, "folder", "thumbnails"));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Thumbnail upload failed: " + msg));
        }
    }

    /**
     * Upload a chapter or sub-chapter cover image.
     * POST /api/materials/upload/module-image
     * Header: Authorization: Bearer <token>  (any logged-in user)
     * Body: multipart/form-data { file: <image file> }
     * Returns: { "success": true, "url": "https://res.cloudinary.com/dt6rxrpqr/image/upload/cyberlearnix/modules/...", "folder": "modules" }
     *
     * Images land in Cloudinary folder  cyberlearnix/modules/
     * so the URL clearly shows it belongs to a chapter/sub-chapter, not a course thumbnail.
     */
    @PostMapping(value = "/upload/module-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadModuleImage(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed for module images"));
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Module image must be under 5MB"));
        }

        try {
            String url = cloudinaryService.uploadImage(file, "cyberlearnix/modules");
            return ResponseEntity.ok(Map.of("success", true, "url", url, "folder", "modules"));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Module image upload failed: " + msg));
        }
    }

    /**
     * Upload a lecture video.
     * POST /api/materials/upload/video
     * Header: Authorization: Bearer <token>
     * Body: multipart/form-data { file: <video file> }
     * Returns: { "success": true, "url": "https://res.cloudinary.com/...", "duration": 120 }
     */
    @PostMapping(value = "/upload/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }

        // Only admin/teacher can upload videos
        if (!"admin".equals(userRole) && !"teacher".equals(userRole) && !"dual".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only teachers and admins can upload videos"));
        }

        // Validate type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only video files are allowed"));
        }

        // Max 500MB
        if (file.getSize() > 500L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Video must be under 500MB"));
        }

        try {
            String url = cloudinaryService.uploadVideo(file, "cyberlearnix/lectures");
            return ResponseEntity.ok(Map.of("success", true, "url", url));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Video upload failed: " + msg));
        }
    }

    /**
     * Upload a document/attachment (PDF, ZIP, etc.)
     * POST /api/materials/upload/document
     * Header: Authorization: Bearer <token>
     * Body: multipart/form-data { file: <document file> }
     * Returns: { "success": true, "url": "https://res.cloudinary.com/..." }
     */
    @PostMapping(value = "/upload/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }

        if (!"admin".equals(userRole) && !"teacher".equals(userRole) && !"dual".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only teachers and admins can upload documents"));
        }

        // Max 50MB
        if (file.getSize() > 50L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Document must be under 50MB"));
        }

        try {
            String url = cloudinaryService.uploadDocument(file, "cyberlearnix/attachments");
            return ResponseEntity.ok(Map.of("success", true, "url", url));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Document upload failed: " + msg));
        }
    }

    /**
     * Upload a banner or promo image.
     * POST /api/materials/upload/banner
     */
    @PostMapping(value = "/upload/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadBanner(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        if (!"admin".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only admins can upload banners"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed for banners"));
        }

        try {
            String url = cloudinaryService.uploadImage(file, "cyberlearnix/banners");
            return ResponseEntity.ok(Map.of("success", true, "url", url));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Banner upload failed: " + msg));
        }
    }
}
