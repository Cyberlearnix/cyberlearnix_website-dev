package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.CourseTeacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CourseTeacherRepository extends JpaRepository<CourseTeacher, Object> {
    List<CourseTeacher> findByTeacherId(String teacherId);

    List<CourseTeacher> findByCourseId(Long courseId);

    void deleteByCourseIdAndTeacherId(Long courseId, String teacherId);

    boolean existsByCourseIdAndTeacherId(Long courseId, String teacherId);
}
