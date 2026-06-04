package com.cyberlearnix.cms.controller;

import com.cyberlearnix.cms.service.MediaService;
import com.cyberlearnix.shared.entity.cms.MediaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cms/media")
public class MediaController {

    @Autowired
    private MediaService mediaService;

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
