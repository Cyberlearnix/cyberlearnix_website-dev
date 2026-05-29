package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.service.GoogleDriveService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Handles all media uploads (images, videos, documents) to Google Drive.
 * All uploaded content is stored on Google Drive; the viewUrl / streamUrl
 * is returned and saved in the database.
 *
 * Upload Flow:
 *   1. Frontend sends multipart/form-data POST to /api/materials/upload
 *   2. Backend uploads to Google Drive, gets fileId + URLs
 *   3. Frontend uses those URLs when saving course/lecture/profile data
 */
@RestController
@RequestMapping("/api/materials")
public class MaterialUploadController {

    private static final String AUTH_REQUIRED = "Authentication required";
    private static final String KEY_ERROR = "error";
    private static final String KEY_SUCCESS = "success";

    @Autowired
    private GoogleDriveService googleDriveService;

    /**
     * Upload a course thumbnail image.
     * POST /api/materials/upload/thumbnail
     * Header: Authorization: Bearer <token>
     * Body: multipart/form-data { file: <image file> }
     * Returns: { "success": true, "url": "https://drive.google.com/uc?export=view&id=...", "fileId", "viewUrl", "streamUrl", "name", "folder" }
     */
    @PostMapping(value = {"/upload/thumbnail", "/drive/upload/thumbnail"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadThumbnail(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_ERROR, AUTH_REQUIRED));
        }
        if (!googleDriveService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(KEY_ERROR, "Google Drive upload is not configured on this server"));
        }

        // Validate type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Only image files are allowed for thumbnails"));
        }

        // Max 5MB
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Thumbnail must be under 5MB"));
        }

        try {
            Map<String, String> result = googleDriveService.uploadFile(file);
            String fileId = result.get("fileId");
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    "fileId",    fileId,
                    "url",       "https://drive.google.com/uc?export=view&id=" + fileId,
                    "viewUrl",   result.get("viewUrl"),
                    "streamUrl", result.get("streamUrl"),
                    "name",      result.get("name"),
                    "folder",    "thumbnails"
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_SUCCESS, false, KEY_ERROR, "Thumbnail upload failed: " + msg));
        }
    }

    /**
     * Upload a chapter or sub-chapter cover image.
     * POST /api/materials/upload/module-image
     * Header: Authorization: Bearer <token>  (any logged-in user)
     * Body: multipart/form-data { file: <image file> }
     * Returns: { "success": true, "url": "https://drive.google.com/uc?export=view&id=...", "fileId", "viewUrl", "streamUrl", "name", "folder": "modules" }
     */
    @PostMapping(value = "/upload/module-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadModuleImage(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_ERROR, AUTH_REQUIRED));
        }
        if (!googleDriveService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(KEY_ERROR, "Google Drive upload is not configured on this server"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Only image files are allowed for module images"));
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Module image must be under 5MB"));
        }

        try {
            Map<String, String> result = googleDriveService.uploadFile(file);
            String fileId = result.get("fileId");
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    "fileId",    fileId,
                    "url",       "https://drive.google.com/uc?export=view&id=" + fileId,
                    "viewUrl",   result.get("viewUrl"),
                    "streamUrl", result.get("streamUrl"),
                    "name",      result.get("name"),
                    "folder",    "modules"
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_SUCCESS, false, KEY_ERROR, "Module image upload failed: " + msg));
        }
    }

    /**
     * Upload a lecture video to Google Drive.
     * POST /api/materials/upload/video
     * Header: Authorization: Bearer <token>
     * Body: multipart/form-data { file: <video file> }
     * Returns: { "success": true, "fileId", "viewUrl", "streamUrl", "name" }
     */
    @PostMapping(value = "/upload/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_ERROR, AUTH_REQUIRED));
        }

        // Only admin/teacher can upload videos
        if (!"admin".equals(userRole) && !"teacher".equals(userRole) && !"dual".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_ERROR, "Only teachers and admins can upload videos"));
        }
        if (!googleDriveService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(KEY_ERROR, "Google Drive upload is not configured on this server"));
        }

        // Validate type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Only video files are allowed"));
        }

        // Max 500MB
        if (file.getSize() > 500L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Video must be under 500MB"));
        }

        try {
            Map<String, String> result = googleDriveService.uploadFile(file);
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    "fileId",    result.get("fileId"),
                    "viewUrl",   result.get("viewUrl"),
                    "streamUrl", result.get("streamUrl"),
                    "name",      result.get("name")
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_SUCCESS, false, KEY_ERROR, "Video upload failed: " + msg));
        }
    }

    /**
     * Upload a document/attachment (PDF, ZIP, etc.) to Google Drive.
     * POST /api/materials/upload/document
     * Header: Authorization: Bearer <token>
     * Body: multipart/form-data { file: <document file> }
     * Returns: { "success": true, "fileId", "viewUrl", "streamUrl", "name" }
     */
    @PostMapping(value = "/upload/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_ERROR, AUTH_REQUIRED));
        }

        if (!"admin".equals(userRole) && !"teacher".equals(userRole) && !"dual".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_ERROR, "Only teachers and admins can upload documents"));
        }
        if (!googleDriveService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(KEY_ERROR, "Google Drive upload is not configured on this server"));
        }

        // Max 100MB
        if (file.getSize() > 100L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Document must be under 100MB"));
        }

        try {
            Map<String, String> result = googleDriveService.uploadFile(file);
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    "fileId",    result.get("fileId"),
                    "viewUrl",   result.get("viewUrl"),
                    "streamUrl", result.get("streamUrl"),
                    "name",      result.get("name")
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_SUCCESS, false, KEY_ERROR, "Document upload failed: " + msg));
        }
    }

    // ─── Google Drive endpoints ────────────────────────────────────────────────

    /**
     * Upload a lecture video to Google Drive.
     * POST /api/materials/drive/upload/video
     * Header: Authorization: Bearer <token>  (teacher or admin only)
     * Body: multipart/form-data { file: <video file> }
     * Returns: { success, fileId, viewUrl, streamUrl, name }
     */
    @PostMapping(value = "/drive/upload/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> driveUploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_ERROR, AUTH_REQUIRED));
        }
        if (!"admin".equals(userRole) && !"teacher".equals(userRole) && !"dual".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(KEY_ERROR, "Only teachers and admins can upload videos"));
        }
        if (!googleDriveService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(KEY_ERROR, "Google Drive upload is not configured on this server"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Only video files are allowed"));
        }
        if (file.getSize() > 500L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Video must be under 500 MB"));
        }

        try {
            Map<String, String> result = googleDriveService.uploadFile(file);
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    "fileId",    result.get("fileId"),
                    "viewUrl",   result.get("viewUrl"),
                    "streamUrl", result.get("streamUrl"),
                    "name",      result.get("name")
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_SUCCESS, false, KEY_ERROR, "Drive video upload failed: " + msg));
        }
    }

    /**
     * Upload a document (PDF, ZIP, etc.) to Google Drive.
     * POST /api/materials/drive/upload/document
     * Header: Authorization: Bearer <token>  (teacher or admin only)
     * Body: multipart/form-data { file: <document file> }
     * Returns: { success, fileId, viewUrl, streamUrl, name }
     */
    @PostMapping(value = "/drive/upload/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> driveUploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_ERROR, AUTH_REQUIRED));
        }
        if (!"admin".equals(userRole) && !"teacher".equals(userRole) && !"dual".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(KEY_ERROR, "Only teachers and admins can upload documents"));
        }
        if (!googleDriveService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(KEY_ERROR, "Google Drive upload is not configured on this server"));
        }

        if (file.getSize() > 100L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Document must be under 100 MB"));
        }

        try {
            Map<String, String> result = googleDriveService.uploadFile(file);
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    "fileId",    result.get("fileId"),
                    "viewUrl",   result.get("viewUrl"),
                    "streamUrl", result.get("streamUrl"),
                    "name",      result.get("name")
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_SUCCESS, false, KEY_ERROR, "Drive document upload failed: " + msg));
        }
    }

    /**
     * Proxy-stream a file from Google Drive to the client.
     * Forwards the Range header so the <video> element can seek.
     * GET /api/materials/drive/stream/{fileId}
     * Header: Authorization: Bearer <token>  (any authenticated user)
     */
    @GetMapping("/drive/stream/{fileId}")
    public void streamFromDrive(
            @PathVariable String fileId,
            @RequestHeader(value = "Range", required = false) String range,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            HttpServletResponse response) throws IOException {

        if (userId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, AUTH_REQUIRED);
            return;
        }
        if (!googleDriveService.isEnabled()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Google Drive not configured");
            return;
        }

        // Validate fileId — allow only alphanumeric + hyphens/underscores (Drive format)
        if (!fileId.matches("[a-zA-Z0-9_\\-]{10,100}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid file ID");
            return;
        }

        try {
            GoogleDriveService.DriveStream ds = googleDriveService.openStream(fileId, range);
            response.setContentType(ds.mimeType());
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Cache-Control", "public, max-age=86400");
            if (ds.size() > 0) {
                response.setHeader("Content-Length", String.valueOf(ds.size()));
            }
            if (range != null && !range.isBlank()) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            }
            try (InputStream in = ds.inputStream()) {
                byte[] buf = new byte[65536];
                int read;
                var out = response.getOutputStream();
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
            }
        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Stream error: " + e.getMessage());
            }
        }
    }

    /**
     * Upload a banner or promo image to Google Drive.
     * POST /api/materials/upload/banner
     * Returns: { "success": true, "url": "https://drive.google.com/uc?export=view&id=...", "fileId", "viewUrl", "streamUrl", "name" }
     */
    @PostMapping(value = "/upload/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadBanner(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_ERROR, AUTH_REQUIRED));
        }
        if (!"admin".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_ERROR, "Only admins can upload banners"));
        }
        if (!googleDriveService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(KEY_ERROR, "Google Drive upload is not configured on this server"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of(KEY_ERROR, "Only image files are allowed for banners"));
        }

        try {
            Map<String, String> result = googleDriveService.uploadFile(file);
            String fileId = result.get("fileId");
            return ResponseEntity.ok(Map.of(
                    KEY_SUCCESS, true,
                    "fileId",    fileId,
                    "url",       "https://drive.google.com/uc?export=view&id=" + fileId,
                    "viewUrl",   result.get("viewUrl"),
                    "streamUrl", result.get("streamUrl"),
                    "name",      result.get("name")
            ));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_SUCCESS, false, KEY_ERROR, "Banner upload failed: " + msg));
        }
    }

    // ─── Drive-prefixed image upload aliases (matches frontend /drive/upload/* calls) ─

    /**
     * POST /api/materials/drive/upload/thumbnail  — alias for /upload/thumbnail
     */
    @PostMapping(value = "/drive/upload/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> driveUploadThumbnail(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        return uploadThumbnail(file, userId, userRole);
    }

    /**
     * POST /api/materials/drive/upload/module-image  — alias for /upload/module-image
     */
    @PostMapping(value = "/drive/upload/module-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> driveUploadModuleImage(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        return uploadModuleImage(file, userId, userRole);
    }

    /**
     * POST /api/materials/drive/upload/banner  — alias for /upload/banner
     */
    @PostMapping(value = "/drive/upload/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> driveUploadBanner(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        return uploadBanner(file, userId, userRole);
    }
}
