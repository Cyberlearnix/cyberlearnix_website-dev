package com.cyberlearnix.course.service;

import com.cyberlearnix.shared.entity.course.Course;
import com.cyberlearnix.shared.entity.course.CourseModule;
import com.cyberlearnix.shared.entity.course.ModuleContent;
import com.cyberlearnix.shared.repository.course.*;
import com.cyberlearnix.course.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class CourseManagementService {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseModuleRepository moduleRepository;

    @Autowired
    private ModuleContentRepository contentRepository;

    @Autowired
    private CourseTeacherRepository courseTeacherRepository;

    @Autowired
    private UserServiceClient userServiceClient;

    public boolean hasPermission(String userId, String permission) {
        try {
            Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
            if (perm == null) return false;
            return switch (permission) {
                case "can_create_courses" -> Boolean.TRUE.equals(perm.get("canCreateCourses"));
                case "can_edit_courses" -> Boolean.TRUE.equals(perm.get("canEditCourses"));
                case "can_delete_courses" -> Boolean.TRUE.equals(perm.get("canDeleteCourses"));
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canEditCourse(Long courseId, String userId) {
        if (!courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) return false;
        try {
            Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
            return perm != null && Boolean.TRUE.equals(perm.get("canEditCourses"));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canDeleteCourse(Long courseId, String userId) {
        try {
            Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
            return perm != null && Boolean.TRUE.equals(perm.get("canDeleteCourses"));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canViewCourse(Long courseId, String userId) {
        return courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId);
    }

    public boolean canManageCourse(Long courseId, String userId, String permission) {
        if (!courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) return false;
        try {
            Map<String, Object> perm = userServiceClient.getTeacherPermission(userId);
            if (perm == null) return false;
            return switch (permission) {
                case "can_add_modules" -> Boolean.TRUE.equals(perm.get("canAddModules"));
                case "can_edit_modules" -> Boolean.TRUE.equals(perm.get("canEditModules"));
                case "can_delete_modules" -> Boolean.TRUE.equals(perm.get("canDeleteModules"));
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canManageModule(Long moduleId, String userId, String permission) {
        Optional<CourseModule> module = moduleRepository.findById(moduleId);
        if (module.isEmpty()) return false;
        return canManageCourse(module.get().getCourse().getId(), userId, permission);
    }

    public boolean canManageContent(Long contentId, String userId, String permission) {
        Optional<ModuleContent> content = contentRepository.findById(contentId);
        if (content.isEmpty()) return false;
        return canManageCourse(content.get().getModule().getCourse().getId(), userId, permission);
    }
}