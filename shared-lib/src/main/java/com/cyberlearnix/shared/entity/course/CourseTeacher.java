package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

@Data
@Entity
@Table(name = "course_teachers")
@IdClass(CourseTeacherId.class)
public class CourseTeacher {
    @Id
    @Column(name = "course_id")
    private Long courseId;

    @Id
    @Column(name = "teacher_id")
    private String teacherId;
}
