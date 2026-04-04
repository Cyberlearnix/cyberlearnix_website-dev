package com.cyberlearnix.shared.entity;

import lombok.Data;
import java.io.Serializable;

@Data
public class CourseTeacherId implements Serializable {
    private Long courseId;
    private String teacherId;
}
