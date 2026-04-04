package com.cyberlearnix.cms.controller;

import com.cyberlearnix.cms.service.MediaService;
import com.cyberlearnix.shared.entity.MediaFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

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
            @RequestParam(value = "uploadedBy", required = false) String uploadedBy) {
        return ResponseEntity.ok(mediaService.uploadMedia(file, type, uploadedBy));
    }
}
