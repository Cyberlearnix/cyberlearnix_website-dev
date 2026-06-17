package com.cyberlearnix.shared.repository.identity;

import com.cyberlearnix.shared.entity.identity.IdentityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IdentityAuditLogRepository extends JpaRepository<IdentityAuditLog, String> {
    List<IdentityAuditLog> findByMemberIdOrderByTimestampDesc(String memberId);
    List<IdentityAuditLog> findTop50ByOrderByTimestampDesc();
}
