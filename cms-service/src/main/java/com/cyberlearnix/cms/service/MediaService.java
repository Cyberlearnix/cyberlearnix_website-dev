package com.cyberlearnix.cms.service;

import com.cyberlearnix.shared.entity.cms.MediaFile;
import com.cyberlearnix.shared.repository.MediaFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MediaService {

    @Autowired
    private MediaFileRepository mediaFileRepository;

    public List<MediaFile> getAllMedia() {
        return mediaFileRepository.findAll();
    }

    public MediaFile uploadMedia(MultipartFile file, String type, String uploadedBy) {
        // In a real application, you would upload to AWS S3/Cloudinary here.
        // For now, we mock the URL and store the metadata.
        String mockUrl = "https://cdn.cyberlearnix.com/cms/" + type + "s/" + file.getOriginalFilename();
        
        MediaFile mediaFile = new MediaFile();
        mediaFile.setName(file.getOriginalFilename());
        mediaFile.setUrl(mockUrl);
        mediaFile.setType(type);
        mediaFile.setSize(file.getSize());
        mediaFile.setUploadedBy(uploadedBy);
        mediaFile.setCreatedAt(LocalDateTime.now());
        
        return mediaFileRepository.save(mediaFile);
    }
}
