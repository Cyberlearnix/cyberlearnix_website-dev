package com.cyberlearnix.shared.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;

/**
 * Cloudinary upload service shared across all microservices.
 * Credentials: cloud_name=dt6rxrpqr, upload_preset=ml_default
 */
@Service
public class CloudinaryService {

    @Value("${cloudinary.cloud-name:dt6rxrpqr}")
    private String cloudName;

    @Value("${cloudinary.api-key:#{null}}")
    private String apiKey;

    @Value("${cloudinary.api-secret:#{null}}")
    private String apiSecret;

    @Value("${cloudinary.upload-preset:ml_default}")
    private String uploadPreset;

    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        if (apiKey != null && apiSecret != null) {
            // Signed upload (more secure — server controls uploads)
            cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true
            ));
        } else {
            // Unsigned upload using preset (for development)
            cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "secure", true
            ));
        }
    }

    /**
     * Upload any file (image, video, pdf, etc.) to Cloudinary.
     * Returns the secure_url of the uploaded file.
     *
     * @param file       MultipartFile from HTTP request
     * @param folder     Cloudinary folder, e.g. "cyberlearnix/profiles"
     * @param resourceType "image", "video", "raw", or "auto"
     */
    public String upload(MultipartFile file, String folder, String resourceType) throws IOException {
        Map<?, ?> result;
        if (apiKey != null && apiSecret != null) {
            // Signed upload — all params allowed
            Map<?, ?> params = ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", resourceType,
                    "overwrite", false,
                    "unique_filename", true
            );
            result = cloudinary.uploader().upload(file.getBytes(), params);
        } else {
            // Unsigned upload — Cloudinary only allows: folder, public_id, tags, context, metadata, etc.
            // overwrite and unique_filename are NOT permitted for unsigned uploads
            Map<?, ?> params = ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", resourceType
            );
            result = cloudinary.uploader().unsignedUpload(file.getBytes(), uploadPreset, params);
        }

        String secureUrl = (String) result.get("secure_url");
        if (secureUrl == null) {
            throw new IOException("Cloudinary upload failed: no secure_url returned");
        }
        return secureUrl;
    }

    /**
     * Upload an image (profile photos, thumbnails, banners).
     */
    public String uploadImage(MultipartFile file, String folder) throws IOException {
        return upload(file, folder, "image");
    }

    /**
     * Upload a video (lecture videos).
     */
    public String uploadVideo(MultipartFile file, String folder) throws IOException {
        return upload(file, folder, "video");
    }

    /**
     * Upload a document (PDFs, attachments).
     */
    public String uploadDocument(MultipartFile file, String folder) throws IOException {
        return upload(file, folder, "raw");
    }

    /**
     * Delete a file from Cloudinary by its public_id.
     */
    public boolean delete(String publicId, String resourceType) {
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId,
                    ObjectUtils.asMap("resource_type", resourceType));
            return "ok".equals(result.get("result"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extract the public_id from a Cloudinary secure_url.
     * e.g. https://res.cloudinary.com/dt6rxrpqr/image/upload/v123/cyberlearnix/profiles/abc.jpg
     *   → cyberlearnix/profiles/abc
     */
    public static String extractPublicId(String secureUrl) {
        if (secureUrl == null || !secureUrl.contains("/upload/")) return null;
        String afterUpload = secureUrl.substring(secureUrl.indexOf("/upload/") + 8);
        // Remove version prefix (v12345/)
        if (afterUpload.startsWith("v") && afterUpload.contains("/")) {
            afterUpload = afterUpload.substring(afterUpload.indexOf("/") + 1);
        }
        // Remove file extension
        int lastDot = afterUpload.lastIndexOf('.');
        if (lastDot > 0) {
            afterUpload = afterUpload.substring(0, lastDot);
        }
        return afterUpload;
    }
}
