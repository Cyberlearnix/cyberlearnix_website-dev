package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teacher_permissions")
public class TeacherPermission {
    @Id
    @Column(name = "teacher_id")
    private String teacherId;

    // Course Management Permissions
    @Column(name = "can_create_courses")
    private Boolean canCreateCourses = false;

    @Column(name = "can_edit_courses")
    private Boolean canEditCourses = true;

    @Column(name = "can_delete_courses")
    private Boolean canDeleteCourses = false;

    // Content Management Permissions
    @Column(name = "can_add_modules")
    private Boolean canAddModules = true;

    @Column(name = "can_edit_modules")
    private Boolean canEditModules = true;

    @Column(name = "can_delete_modules")
    private Boolean canDeleteModules = false;

    @Column(name = "can_add_content")
    private Boolean canAddContent = true;

    @Column(name = "can_edit_content")
    private Boolean canEditContent = true;

    @Column(name = "can_delete_content")
    private Boolean canDeleteContent = false;

    @Column(name = "can_manage_exams")
    private Boolean canManageExams = true;

    // Student Management Permissions
    @Column(name = "can_manage_students")
    private Boolean canManageStudents = true;

    @Column(name = "can_grade_assignments")
    private Boolean canGradeAssignments = true;

    @Column(name = "can_view_analytics")
    private Boolean canViewAnalytics = true;

    // Restrictions
    @Column(name = "max_modules_per_course")
    private Integer maxModulesPerCourse = 10;

    @Column(name = "max_content_per_module")
    private Integer maxContentPerModule = 20;

    @Column(name = "granted_by")
    private String grantedBy;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Manual setters for Lombok compatibility
    public void setCanCreateCourses(Boolean canCreateCourses) {
        this.canCreateCourses = canCreateCourses;
    }

    public Boolean getCanCreateCourses() {
        return canCreateCourses;
    }
}
