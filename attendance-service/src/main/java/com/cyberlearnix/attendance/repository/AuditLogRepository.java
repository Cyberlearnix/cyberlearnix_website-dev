package com.cyberlearnix.attendance.repository;

import com.cyberlearnix.attendance.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(String actorId, Pageable pageable);

    Page<AuditLog> findByEntityIdOrderByCreatedAtDesc(String entityId, Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
