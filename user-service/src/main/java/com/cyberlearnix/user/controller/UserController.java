package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.TeacherPermission;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.user.TeacherPermissionRepository;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import com.cyberlearnix.user.dto.UserResponseDTO;
import com.cyberlearnix.user.service.EnrollmentCardService;
import com.cyberlearnix.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private TeacherPermissionRepository teacherPermissionRepository;

    @Autowired
    private EnrollmentCardService enrollmentCardService;

    @PostMapping("/{userId}/generate-card")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> generateEnrollmentCard(@PathVariable String userId) {
        return userProfileRepository.findById(userId)
                .<ResponseEntity<?>>map(profile -> {
                    boolean saved = enrollmentCardService.issueCard(profile);
                    if (!saved) {
                        return ResponseEntity.internalServerError()
                            .body(Map.of("error", "Failed to generate or persist enrollment card. Check server logs."));
                    }
                    // Re-read from DB to confirm the enrollment number was actually persisted.
                    return userProfileRepository.findById(userId)
                            .<ResponseEntity<?>>map(persisted -> {
                                if (persisted.getEnrollmentNumber() == null) {
                                    return ResponseEntity.internalServerError()
                                        .body(Map.of("error", "Card generated but enrollment number was not saved to database."));
                                }
                                return ResponseEntity.ok(Map.of(
                                    "enrollmentNumber", persisted.getEnrollmentNumber(),
                                    "qrCodeData", persisted.getQrCodeData() != null ? persisted.getQrCodeData() : ""
                                ));
                            })
                            .orElse(ResponseEntity.notFound().build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponseDTO>> getAllUsersRoot(
            @RequestParam(required = false) String role) {
        if (role != null && !role.isBlank()) {
            return ResponseEntity.ok(userService.getUsersByRole(role));
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile(Authentication authentication) {
        String userId = authentication.getName();
        return userProfileRepository.findById(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateCurrentUserProfile(Authentication authentication,
            @RequestBody Map<String, Object> updates) {
        String userId = authentication.getName();
        return userProfileRepository.findById(userId)
                .<ResponseEntity<?>>map(profile -> {
                    // Support both camelCase and snake_case field names from frontend
                    String fullName = updates.containsKey("fullName") ? (String) updates.get("fullName") : (String) updates.get("full_name");
                    if (fullName != null) profile.setFullName(fullName);
                    
                    String phone = updates.containsKey("phone") ? (String) updates.get("phone") : (String) updates.get("phone_number");
                    if (phone != null) profile.setPhone(phone);
                    
                    String photoUrl = updates.containsKey("photoUrl") ? (String) updates.get("photoUrl") : (String) updates.get("avatar_url");
                    if (photoUrl != null) profile.setPhotoUrl(photoUrl);
                    
                    String dateOfBirth = updates.containsKey("dateOfBirth") ? (String) updates.get("dateOfBirth") : (String) updates.get("date_of_birth");
                    if (dateOfBirth != null) profile.setDateOfBirth(dateOfBirth);
                    Object age = updates.get("age");
                    if (age instanceof Number) profile.setAge(((Number) age).intValue());
                    else if (age instanceof String && !((String) age).isBlank()) profile.setAge(Integer.parseInt((String) age));
                    if (updates.containsKey("bio") && updates.get("bio") != null)
                        profile.setBio((String) updates.get("bio"));
                    if (updates.containsKey("location") && updates.get("location") != null)
                        profile.setLocation((String) updates.get("location"));
                    profile.setUpdatedAt(LocalDateTime.now());
                    profile.setIsProfileComplete(true);
                    return ResponseEntity.ok(userProfileRepository.save(profile));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<?> getUserProfile(@PathVariable String userId) {
        return userProfileRepository.findById(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{userId}/teacher-permission")
    public ResponseEntity<?> getTeacherPermission(@PathVariable String userId) {
        return teacherPermissionRepository.findById(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{teacherId}/teacher-permission")
    public ResponseEntity<?> updateTeacherPermission(@PathVariable String teacherId,
            @RequestBody Map<String, Object> permissions) {
        TeacherPermission perm = teacherPermissionRepository.findById(teacherId)
                .orElse(new TeacherPermission());
        perm.setTeacherId(teacherId);
        if (permissions.containsKey("canCreateCourses"))
            perm.setCanCreateCourses(Boolean.TRUE.equals(permissions.get("canCreateCourses")));
        if (permissions.containsKey("canEditCourses"))
            perm.setCanEditCourses(Boolean.TRUE.equals(permissions.get("canEditCourses")));
        if (permissions.containsKey("canDeleteCourses"))
            perm.setCanDeleteCourses(Boolean.TRUE.equals(permissions.get("canDeleteCourses")));
        if (permissions.containsKey("canAddModules"))
            perm.setCanAddModules(Boolean.TRUE.equals(permissions.get("canAddModules")));
        if (permissions.containsKey("canEditModules"))
            perm.setCanEditModules(Boolean.TRUE.equals(permissions.get("canEditModules")));
        if (permissions.containsKey("canDeleteModules"))
            perm.setCanDeleteModules(Boolean.TRUE.equals(permissions.get("canDeleteModules")));
        if (permissions.containsKey("canAddContent"))
            perm.setCanAddContent(Boolean.TRUE.equals(permissions.get("canAddContent")));
        if (permissions.containsKey("canEditContent"))
            perm.setCanEditContent(Boolean.TRUE.equals(permissions.get("canEditContent")));
        if (permissions.containsKey("canDeleteContent"))
            perm.setCanDeleteContent(Boolean.TRUE.equals(permissions.get("canDeleteContent")));
        if (permissions.containsKey("canManageExams"))
            perm.setCanManageExams(Boolean.TRUE.equals(permissions.get("canManageExams")));
        if (permissions.containsKey("canManageStudents"))
            perm.setCanManageStudents(Boolean.TRUE.equals(permissions.get("canManageStudents")));
        return ResponseEntity.ok(teacherPermissionRepository.save(perm));
    }
}
