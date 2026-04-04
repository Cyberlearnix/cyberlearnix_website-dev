package com.cyberlearnix.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private String action;
    private String recipient;
    private String subject;
    private String templateName;
    private Map<String, String> data;
}
