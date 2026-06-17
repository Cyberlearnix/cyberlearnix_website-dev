package com.cyberlearnix.shared.entity.identity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "identity_audit_logs")
public class IdentityAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "member_id")
    private String memberId; // Reference to Member's custom memberId (CLX-...) or database ID

    @Column(nullable = false)
    private String action; // SUBMITTED, APPROVED, REJECTED, CHANGES_REQUESTED, PROMOTED, DEACTIVATED, DEPT_TRANSFER

    @Column(name = "performed_by", nullable = false)
    private String performedBy; // Email or name of the admin who performed the action

    @Column(columnDefinition = "TEXT", nullable = false)
    private String details;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
}
