package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.service.GoogleDriveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Handles profile photo uploads to Google Drive.
 * POST /api/users/upload/photo  — authenticated users only
 *
 * The frontend should:
 *   1. POST the file here → receives a Drive viewUrl
 *   2. PATCH /api/users/profile with { "photoUrl": "<viewUrl>" }
 */
@RestController
@RequestMapping("/api/users")
public class PhotoUploadController {

    @Autowired
    private GoogleDriveService googleDriveService;

    /**
     * Upload a profile photo to Google Drive.
     * Returns the public view URL to save as the user's photoUrl.
     */
    @PostMapping(value = "/upload/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        if (!googleDriveService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "File storage is not configured on this server"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only image files are allowed"));
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Photo must be under 5MB"));
        }

        try {
            Map<String, String> result = googleDriveService.uploadFile(file);
            String fileId = result.get("fileId");
            String viewUrl = "https://drive.google.com/uc?export=view&id=" + fileId;
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "url",     viewUrl,
                    "fileId",  fileId
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Photo upload failed: " + msg));
        }
    }
}
