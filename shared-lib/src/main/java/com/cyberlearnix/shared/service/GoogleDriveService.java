package com.cyberlearnix.shared.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Google Drive upload / stream service.
 *
 * Configuration (application.properties or env):
 *   google.drive.credentials-json-b64  — base64-encoded service account JSON
 *   google.drive.folder-id             — Drive folder ID to upload into
 *
 * If credentials are not set the service is disabled and isEnabled() returns false.
 * All callers must check isEnabled() before using upload/stream methods.
 */
@Service
public class GoogleDriveService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String APP_NAME = "CyberLearnix LMS";

    @Value("${google.drive.credentials-json-b64:}")
    private String credentialsJsonB64;

    @Value("${google.drive.folder-id:}")
    private String folderId;

    private Drive drive;
    private boolean enabled = false;

    @PostConstruct
    public void init() {
        if (credentialsJsonB64 == null || credentialsJsonB64.isBlank()) {
            log.info("Google Drive not configured — upload via Drive is disabled");
            return;
        }
        try {
            byte[] json = Base64.getDecoder().decode(credentialsJsonB64.trim());
            var credentials = ServiceAccountCredentials
                    .fromStream(new ByteArrayInputStream(json))
                    .createScoped(Collections.singleton(DriveScopes.DRIVE));
            drive = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APP_NAME)
                    .build();
            enabled = true;
            log.info("Google Drive service initialized successfully");
        } catch (Exception ex) {
            log.error("Failed to initialize Google Drive service: {}", ex.getMessage());
        }
    }

    /** Returns true when credentials are configured and the Drive client is ready. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Upload a file to Google Drive under the configured folder.
     * The file is made publicly readable (anyone with the link).
     *
     * @param file the multipart file from the HTTP request
     * @return map with keys: fileId, viewUrl, streamUrl, name
     */
    public Map<String, String> uploadFile(MultipartFile file) throws IOException {
        assertEnabled();

        File meta = new File();
        meta.setName(sanitize(file.getOriginalFilename()));
        if (folderId != null && !folderId.isBlank()) {
            meta.setParents(List.of(folderId));
        }

        var content = new com.google.api.client.http.InputStreamContent(
                file.getContentType(), file.getInputStream());
        content.setLength(file.getSize());

        File uploaded = drive.files().create(meta, content)
                .setFields("id, name, mimeType")
                .execute();

        String id = uploaded.getId();

        // Grant public read access (anyone with the link)
        drive.permissions()
                .create(id, new Permission().setType("anyone").setRole("reader"))
                .execute();

        log.info("Uploaded file to Drive: id={} name={}", id, uploaded.getName());

        return Map.of(
                "fileId",    id,
                "viewUrl",   "https://drive.google.com/file/d/" + id + "/view",
                "streamUrl", "/api/materials/drive/stream/" + id,
                "name",      uploaded.getName() != null ? uploaded.getName() : ""
        );
    }

    /**
     * Open a stream to download/proxy a Drive file.
     * Optionally forwards an HTTP Range header for seek support.
     *
     * @param fileId      the Drive file ID
     * @param rangeHeader value of the HTTP Range header, or null for full file
     * @return DriveStream record containing the InputStream, MIME type, and file size
     */
    public DriveStream openStream(String fileId, String rangeHeader) throws IOException {
        assertEnabled();

        // Get metadata
        File meta = drive.files().get(fileId)
                .setFields("id, mimeType, size")
                .execute();

        // Get the media stream, forwarding Range header if present
        Drive.Files.Get mediaReq = drive.files().get(fileId);
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            mediaReq.getRequestHeaders().set("Range", rangeHeader);
        }
        InputStream stream = mediaReq.executeMediaAsInputStream();

        return new DriveStream(
                stream,
                meta.getMimeType() != null ? meta.getMimeType() : "application/octet-stream",
                meta.getSize() != null ? meta.getSize() : -1L
        );
    }

    /**
     * Delete a file from Drive by its file ID.
     * Returns true on success, false on error.
     */
    public boolean deleteFile(String fileId) {
        if (!enabled) return false;
        try {
            drive.files().delete(fileId).execute();
            log.info("Deleted Drive file: {}", fileId);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to delete Drive file {}: {}", fileId, ex.getMessage());
            return false;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void assertEnabled() {
        if (!enabled) {
            throw new IllegalStateException(
                    "Google Drive is not configured. Set GOOGLE_DRIVE_CREDENTIALS_JSON_B64 env variable.");
        }
    }

    /** Strip characters that are unsafe in Drive file names. */
    private String sanitize(String name) {
        if (name == null) return "upload";
        return name.replaceAll("[^a-zA-Z0-9._\\- ]", "_");
    }

    // ── Value type ────────────────────────────────────────────────────────────

    /**
     * Holds the result of openStream(): an InputStream plus metadata
     * needed to set correct HTTP response headers.
     */
    public record DriveStream(InputStream inputStream, String mimeType, long size) {}
}
