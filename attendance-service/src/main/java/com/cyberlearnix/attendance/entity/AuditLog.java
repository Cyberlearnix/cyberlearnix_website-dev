package com.cyberlearnix.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "attendance_audit_logs", indexes = {
    @Index(name = "idx_aal_actor_id", columnList = "actorId"),
    @Index(name = "idx_aal_entity_id", columnList = "entityId"),
    @Index(name = "idx_aal_created_at", columnList = "createdAt")
})
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entity_type")
    private String entityType; // MEETING | ATTENDANCE | CERTIFICATE

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
