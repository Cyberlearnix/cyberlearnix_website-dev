package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.TeacherPermission;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.user.TeacherPermissionRepository;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import com.cyberlearnix.user.dto.UserResponseDTO;
import com.cyberlearnix.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
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
