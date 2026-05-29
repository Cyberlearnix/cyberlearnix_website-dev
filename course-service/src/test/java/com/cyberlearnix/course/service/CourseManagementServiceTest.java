package com.cyberlearnix.course.service;

import com.cyberlearnix.course.client.UserServiceClient;
import com.cyberlearnix.shared.repository.course.CourseRepository;
import com.cyberlearnix.shared.repository.course.CourseTeacherRepository;
import com.cyberlearnix.shared.repository.course.ModuleContentRepository;
import com.cyberlearnix.shared.repository.course.CourseModuleRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CourseManagementService}.
 *
 * The Feign client and all JPA repositories are mocked so no Spring context or
 * network call is required.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CourseManagementServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseModuleRepository moduleRepository;
    @Mock private ModuleContentRepository contentRepository;
    @Mock private CourseTeacherRepository courseTeacherRepository;
    @Mock private UserServiceClient userServiceClient;

    @InjectMocks
    private CourseManagementService service;

    // ── hasPermission ────────────────────────────────────────────────────────────

    @Test
    void hasPermission_returnsFalse_whenFeignClientThrowsException() {
        when(userServiceClient.getTeacherPermission("user-1"))
                .thenThrow(new RuntimeException("service unavailable"));

        boolean result = service.hasPermission("user-1", "can_create_courses");

        assertThat(result).isFalse();
    }

    @Test
    void hasPermission_returnsFalse_whenPermissionMapIsNull() {
        when(userServiceClient.getTeacherPermission("user-2")).thenReturn(null);

        assertThat(service.hasPermission("user-2", "can_create_courses")).isFalse();
    }

    @Test
    void hasPermission_returnsTrue_whenCanCreateCoursesIsTrue() {
        when(userServiceClient.getTeacherPermission("user-3"))
                .thenReturn(Map.of("canCreateCourses", Boolean.TRUE));

        assertThat(service.hasPermission("user-3", "can_create_courses")).isTrue();
    }

    @Test
    void hasPermission_returnsFalse_whenPermissionKeyIsFalse() {
        when(userServiceClient.getTeacherPermission("user-4"))
                .thenReturn(Map.of("canCreateCourses", Boolean.FALSE));

        assertThat(service.hasPermission("user-4", "can_create_courses")).isFalse();
    }

    // ── canEditCourse ────────────────────────────────────────────────────────────

    @Test
    void canEditCourse_returnsFalse_whenTeacherNotAssignedToCourse() {
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(42L, "user-5")).thenReturn(false);

        assertThat(service.canEditCourse(42L, "user-5")).isFalse();
    }

    @Test
    void canEditCourse_returnsTrue_whenTeacherAssignedAndHasPermission() {
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(10L, "user-6")).thenReturn(true);
        when(userServiceClient.getTeacherPermission("user-6"))
                .thenReturn(Map.of("canEditCourses", Boolean.TRUE));

        assertThat(service.canEditCourse(10L, "user-6")).isTrue();
    }

    // ── canDeleteCourse (BUG-004) ─────────────────────────────────────────────

    // Guarantees: ownership check happens first — unassigned teacher is denied even before permission lookup
    @Test
    void canDeleteCourse_returnsFalse_whenTeacherNotAssignedToCourse() {
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(50L, "user-7")).thenReturn(false);

        assertThat(service.canDeleteCourse(50L, "user-7")).isFalse();
        verify(userServiceClient, never()).getTeacherPermission("user-7");
    }

    // Guarantees: teacher assigned to the course and holding delete permission is allowed
    @Test
    void canDeleteCourse_returnsTrue_whenTeacherAssignedAndHasDeletePermission() {
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(50L, "user-8")).thenReturn(true);
        when(userServiceClient.getTeacherPermission("user-8"))
                .thenReturn(Map.of("canDeleteCourses", Boolean.TRUE));

        assertThat(service.canDeleteCourse(50L, "user-8")).isTrue();
    }

    // Guarantees: teacher assigned to the course but lacking delete permission is denied
    @Test
    void canDeleteCourse_returnsFalse_whenTeacherAssignedButLacksDeletePermission() {
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(50L, "user-9")).thenReturn(true);
        when(userServiceClient.getTeacherPermission("user-9"))
                .thenReturn(Map.of("canDeleteCourses", Boolean.FALSE));

        assertThat(service.canDeleteCourse(50L, "user-9")).isFalse();
    }

    // Guarantees: BUG-004 regression — a teacher with global canDeleteCourses=true but NOT assigned to this
    // specific course is still denied (ownership must be verified before the global permission flag)
    @Test
    void canDeleteCourse_returnsFalse_whenTeacherHasGlobalPermissionButIsNotAssignedToCourse() {
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(99L, "user-10")).thenReturn(false);

        // Even if we were to stub global permission as true, the short-circuit must prevent the call
        assertThat(service.canDeleteCourse(99L, "user-10")).isFalse();
        verify(userServiceClient, never()).getTeacherPermission("user-10");
    }
}
