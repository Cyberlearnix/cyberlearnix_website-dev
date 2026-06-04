package com.cyberlearnix.cms.service;

import com.cyberlearnix.shared.entity.cms.MediaFile;
import com.cyberlearnix.shared.repository.cms.MediaFileRepository;
import com.cyberlearnix.shared.service.GoogleDriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private GoogleDriveService googleDriveService;

    public List<MediaFile> getAllMedia() {
        return mediaFileRepository.findAll();
    }

    public MediaFile uploadMedia(MultipartFile file, String type, String uploadedBy) {
        String fileUrl;

        if (googleDriveService.isEnabled()) {
            try {
                Map<String, String> result = googleDriveService.uploadFile(file);
                String fileId = result.get("fileId");
                fileUrl = "https://drive.google.com/uc?export=view&id=" + fileId;
                log.info("CMS media uploaded to Google Drive: fileId={}", fileId);
            } catch (Exception e) {
                log.error("Google Drive upload failed for CMS media: {}", e.getMessage());
                throw new RuntimeException("Media upload failed: " + e.getMessage(), e);
            }
        } else {
            log.warn("Google Drive not configured — CMS media upload will use mock URL");
            fileUrl = "https://cdn.cyberlearnix.com/cms/" + type + "s/" + file.getOriginalFilename();
        }

        MediaFile mediaFile = new MediaFile();
        mediaFile.setName(file.getOriginalFilename());
        mediaFile.setUrl(fileUrl);
        mediaFile.setType(type);
        mediaFile.setSize(file.getSize());
        mediaFile.setUploadedBy(uploadedBy);
        mediaFile.setCreatedAt(LocalDateTime.now());

        return mediaFileRepository.save(mediaFile);
    }

    public boolean deleteMedia(String fileId) {
        boolean driveDeleted = googleDriveService.isEnabled() && googleDriveService.deleteFile(fileId);
        // Remove DB record by Drive file ID embedded in the URL
        mediaFileRepository.findAll().stream()
                .filter(m -> m.getUrl() != null && m.getUrl().contains(fileId))
                .forEach(mediaFileRepository::delete);
        return driveDeleted;
    }
}
