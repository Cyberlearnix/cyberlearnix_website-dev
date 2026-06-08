package com.cyberlearnix.cms.controller;

import com.cyberlearnix.cms.service.MediaService;
import com.cyberlearnix.shared.entity.cms.MediaFile;
import com.cyberlearnix.shared.service.GoogleDriveService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cms/media")
public class MediaController {

    @Autowired
    private MediaService mediaService;

    @Autowired
    private GoogleDriveService googleDriveService;

    @GetMapping
    public List<MediaFile> getAllMedia() {
        return mediaService.getAllMedia();
    }

    @PostMapping("/upload")
    public ResponseEntity<MediaFile> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "image") String type,
            @RequestHeader(value = "X-User-Id", required = false) String uploadedBy) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.uploadMedia(file, type, uploadedBy));
    }

    /**
     * Proxy-stream a file from Google Drive.
     * Supports HTTP Range header for video seek/resume.
     * GET /api/cms/media/drive/stream/{fileId}
     */
    @GetMapping("/drive/stream/{fileId}")
    public void streamFromDrive(
            @PathVariable String fileId,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            HttpServletResponse response) throws IOException {

        if (!googleDriveService.isEnabled()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Google Drive is not configured on this server");
            return;
        }

        try {
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
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Stream failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteMedia(@PathVariable String fileId) {
        boolean deleted = mediaService.deleteMedia(fileId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true, "fileId", fileId));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "Failed to delete file from Drive"));
    }
}
