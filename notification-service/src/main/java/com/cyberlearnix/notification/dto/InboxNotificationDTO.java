package com.cyberlearnix.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Outbound DTO returned to clients for in-app notifications. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxNotificationDTO {
    private Long id;
    private String userId;
    private String type;
    private String title;
    private String body;
    private String link;
    private boolean read;
    private LocalDateTime createdAt;
}
