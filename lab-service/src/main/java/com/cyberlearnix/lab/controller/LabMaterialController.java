package com.cyberlearnix.lab.controller;

import com.cyberlearnix.shared.service.GoogleDriveService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Lab content upload / download via Google Drive.
 *
 * Admins and instructors upload lab scripts, PDFs, and resource archives.
 * Students (and the lab containers) stream content back via the proxy endpoint.
 *
 * Upload flow:
 *   POST /api/labs/materials/upload  → Drive → returns { fileId, viewUrl, streamUrl }
 * Stream flow:
 *   GET  /api/labs/materials/drive/stream/{fileId}  → proxied from Drive
 */
@RestController
@RequestMapping("/api/labs/materials")
public class LabMaterialController {

    private static final String KEY_ERROR   = "error";
    private static final String KEY_SUCCESS = "success";

    // 200 MB — lab archives / ISO images can be large
    private static final long MAX_FILE_BYTES = 200L * 1024 * 1024;

    @Autowired
    private GoogleDriveService googleDriveService;

    /**
     * Upload a lab resource file (script, PDF, archive, etc.) to Google Drive.
     * POST /api/labs/materials/upload
     * Roles: ADMIN or INSTRUCTOR only.
     *
     * @param file      multipart file
     * @param userId    injected by gateway JWT filter
     * @return { success, fileId, viewUrl, streamUrl, name }
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<Map<String, Object>> uploadLabMaterial(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(KEY_ERROR, "Authentication required"));
        }

        if (!googleDriveService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(KEY_ERROR, "Google Drive storage is not configured on this server"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "File must not be empty"));
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            return ResponseEntity.badRequest()
                    .body(Map.of(KEY_ERROR, "File exceeds 200 MB limit"));
        }

        try {
            Map<String, String> result = googleDriveService.uploadFile(file);
            String fileId = result.get("fileId");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    KEY_SUCCESS, true,
                    "fileId",    fileId,
                    "viewUrl",   result.get("viewUrl"),
                    "streamUrl", "/api/labs/materials/drive/stream/" + fileId,
                    "name",      result.get("name")
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_SUCCESS, false, KEY_ERROR, "Upload failed: " + msg));
        }
    }

    /**
     * Proxy-stream a lab material from Google Drive.
     * GET /api/labs/materials/drive/stream/{fileId}
     * Accessible to any authenticated user (student reads lab scripts).
     * Supports HTTP Range header for resumable downloads.
     */
    @GetMapping("/drive/stream/{fileId}")
    @PreAuthorize("isAuthenticated()")
    public void streamLabMaterial(
            @PathVariable String fileId,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            HttpServletResponse response) throws IOException {

        if (!googleDriveService.isEnabled()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Google Drive storage is not configured on this server");
            return;
        }

        GoogleDriveService.DriveStream stream = googleDriveService.openStream(fileId, rangeHeader);
        response.setContentType(stream.mimeType());
        if (stream.size() > 0) {
            response.setContentLengthLong(stream.size());
        }
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        }

        try (InputStream in = stream.inputStream()) {
            in.transferTo(response.getOutputStream());
        }
    }

    /**
     * Delete a lab material from Google Drive.
     * DELETE /api/labs/materials/{fileId}
     * Roles: ADMIN only.
     */
    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteLabMaterial(@PathVariable String fileId) {
        if (!googleDriveService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(KEY_ERROR, "Google Drive storage is not configured on this server"));
        }

        boolean deleted = googleDriveService.deleteFile(fileId);
        if (deleted) {
            return ResponseEntity.ok(Map.of(KEY_SUCCESS, true, "fileId", fileId));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(KEY_SUCCESS, false, KEY_ERROR, "Failed to delete file from Drive"));
    }
}
