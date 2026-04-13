package com.cyberlearnix.course.service;

import com.cyberlearnix.shared.entity.course.Course;
import com.cyberlearnix.shared.entity.course.CourseModule;
import com.cyberlearnix.shared.entity.course.ModuleContent;
import com.cyberlearnix.shared.entity.user.TeacherPermission;
import com.cyberlearnix.shared.repository.course.*;
import com.cyberlearnix.shared.repository.user.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private TeacherPermissionRepository permissionRepository;

    @Autowired
    private CourseTeacherRepository courseTeacherRepository;

    public boolean hasPermission(String userId, String permission) {
        Optional<TeacherPermission> perm = permissionRepository.findById(userId);
        return perm.map(p -> {
            switch (permission) {
                case "can_create_courses":
                    return p.getCanCreateCourses();
                case "can_edit_courses":
                    return p.getCanEditCourses();
                case "can_delete_courses":
                    return p.getCanDeleteCourses();
                default:
                    return false;
            }
        }).orElse(false);
    }

    public boolean canEditCourse(Long courseId, String userId) {
        // Check if user is admin or has permission
        Optional<TeacherPermission> perm = permissionRepository.findById(userId);
        if (perm.isEmpty()) return false;

        TeacherPermission permission = perm.get();
        if (!permission.getCanEditCourses()) return false;

        // Check if teacher is assigned to this course
        return courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId);
    }

    public boolean canDeleteCourse(Long courseId, String userId) {
        Optional<TeacherPermission> perm = permissionRepository.findById(userId);
        return perm.map(p -> p.getCanDeleteCourses()).orElse(false);
    }

    public boolean canViewCourse(Long courseId, String userId) {
        // Check if user is admin or assigned teacher
        return courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId);
    }

    public boolean canManageCourse(Long courseId, String userId, String permission) {
        Optional<TeacherPermission> perm = permissionRepository.findById(userId);
        if (perm.isEmpty()) return false;

        TeacherPermission teacherPerm = perm.get();
        boolean hasPermission = switch (permission) {
            case "can_add_modules" -> teacherPerm.getCanAddModules();
            case "can_edit_modules" -> teacherPerm.getCanEditModules();
            case "can_delete_modules" -> teacherPerm.getCanDeleteModules();
            default -> false;
        };

        if (!hasPermission) return false;

        // Check if teacher is assigned to this course
        return courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId);
    }

    public boolean canManageModule(Long moduleId, String userId, String permission) {
        Optional<CourseModule> module = moduleRepository.findById(moduleId);
        if (module.isEmpty()) return false;

        Long courseId = module.get().getCourse().getId();
        return canManageCourse(courseId, userId, permission);
    }

    public boolean canManageContent(Long contentId, String userId, String permission) {
        Optional<ModuleContent> content = contentRepository.findById(contentId);
        if (content.isEmpty()) return false;

        Long courseId = content.get().getModule().getCourse().getId();
        return canManageCourse(courseId, userId, permission);
    }
}
