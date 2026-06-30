package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.identity.Member;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.identity.MemberRepository;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import com.cyberlearnix.shared.service.GoogleDriveService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * Public endpoint — no authentication required.
 * Called when a member's secure QR code is scanned or ID typed.
 */
@RestController
@RequestMapping("/api/public")
public class PublicVerifyController {

    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private GoogleDriveService googleDriveService;

    @GetMapping("/verify/{enrollmentNumber}")
    public ResponseEntity<?> verifyMember(@PathVariable String enrollmentNumber) {
        if (enrollmentNumber == null || enrollmentNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid enrollment or identity number"));
        }

        String searchId = enrollmentNumber.toUpperCase().trim();

        // 1. Try finding in the new centralized Members directory
        Optional<Member> memberOpt = memberRepository.findByMemberId(searchId);
        if (memberOpt.isPresent()) {
            Member m = memberOpt.get();
            if (!Boolean.TRUE.equals(m.getIsActive())) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Account inactive",
                    "message", "This member profile is no longer active"
                ));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("valid", true);
            response.put("id", m.getId());
            response.put("enrollmentNumber", m.getMemberId());
            response.put("fullName", m.getFullName());
            response.put("email", m.getEmail());
            response.put("role", m.getMemberType().toLowerCase());
            
            String photoUrl = m.getProfilePhoto();
            String fileId = extractDriveFileId(photoUrl);
            if (fileId != null) {
                photoUrl = "/api/public/verify/photo/" + fileId;
            }
            response.put("photoUrl", photoUrl);
            
            String locationStr = "CyberLearnix";
            if (m.getDepartment() != null && !m.getDepartment().isBlank()) {
                locationStr = m.getDepartment();
                if (m.getDesignation() != null && !m.getDesignation().isBlank()) {
                    locationStr += " / " + m.getDesignation();
                }
            } else if (m.getDesignation() != null && !m.getDesignation().isBlank()) {
                locationStr = m.getDesignation();
            }
            response.put("location", locationStr);
            response.put("enrolledAt", m.getJoiningDate() != null ? m.getJoiningDate().toString() : m.getCreatedAt().toString());
            response.put("institute", "Cyberlearnix Private Limited");
            return ResponseEntity.ok(response);
        }

        // 2. Fallback to legacy UserProfile table for backwards compatibility
        Optional<UserProfile> profileOpt = userProfileRepository.findByEnrollmentNumber(searchId);
        if (profileOpt.isPresent()) {
            UserProfile p = profileOpt.get();
            if (!Boolean.TRUE.equals(p.getIsActive())) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "Account inactive",
                    "message", "This student account is no longer active"
                ));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("valid", true);
            response.put("id", p.getId());
            response.put("enrollmentNumber", p.getEnrollmentNumber());
            response.put("fullName", p.getFullName() != null ? p.getFullName() : "Student");
            response.put("email", p.getEmail());
            response.put("role", p.getRole() != null ? p.getRole() : "student");
            
            String photoUrl = p.getPhotoUrl();
            String fileId = extractDriveFileId(photoUrl);
            if (fileId != null) {
                photoUrl = "/api/public/verify/photo/" + fileId;
            }
            response.put("photoUrl", photoUrl);
            
            response.put("location", p.getLocation() != null ? p.getLocation() : "Remote / Online");
            response.put("enrolledAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
            response.put("institute", "Cyberlearnix Private Limited");
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(404).body(Map.of(
            "error", "Identity not found",
            "message", "No active profile matches this identity or enrollment number"
        ));
    }

    /**
     * Public endpoint to stream profile photo from Google Drive.
     * Does not require authentication.
     */
    @GetMapping(value = "/verify/photo/{fileId}")
    public void streamPhotoPublicly(
            @PathVariable String fileId,
            @RequestHeader(value = "Range", required = false) String range,
            HttpServletResponse response) throws IOException {

        if (!googleDriveService.isEnabled()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Google Drive storage not configured");
            return;
        }

        if (fileId == null || !fileId.matches("[a-zA-Z0-9_\\-]{10,100}")) {
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
     * Public endpoint to retrieve approved team members for the website about page.
     * Excludes student and intern roles to prevent unauthorized display.
     */
    @GetMapping("/about-members")
    public ResponseEntity<List<Map<String, Object>>> getPublicAboutMembers() {
        List<Member> members = memberRepository.findPublicDirectoryMembers();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Member m : members) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("memberId", m.getMemberId());
            item.put("fullName", m.getFullName());
            item.put("role", m.getMemberType());

            String photoUrl = m.getProfilePhoto();
            if (photoUrl != null && !photoUrl.isBlank()) {
                String fileId = extractDriveFileId(photoUrl);
                if (fileId != null) {
                    photoUrl = "/api/public/verify/photo/" + fileId;
                }
            }
            item.put("photoUrl", photoUrl);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Extracts Google Drive file ID from a URL.
     */
    private String extractDriveFileId(String url) {
        if (url == null || url.isBlank()) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "drive\\.google\\.com/(?:file/d/|uc\\?export=view&id=|uc\\?id=|open\\?id=|thumbnail\\?id=|googleusercontent\\.com/[^/]+/d/)([a-zA-Z0-9_\\-]{10,100})"
        );
        java.util.regex.Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
