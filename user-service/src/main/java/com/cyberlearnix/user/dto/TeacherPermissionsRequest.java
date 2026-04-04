package com.cyberlearnix.user.dto;

import lombok.Data;
import java.util.Map;

@Data
public class TeacherPermissionsRequest {
    private String teacherId;
    private Map<String, Object> permissions;
}
