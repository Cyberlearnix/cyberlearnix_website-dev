package com.cyberlearnix.notification.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class NotificationRequestDTO {
    private List<String> emails;
    private String subject;
    private String message;
    
    // For other actions that use key-value pairs (Object type to support lists/nested structures)
    private Map<String, Object> data;
}
