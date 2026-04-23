package com.cyberlearnix.shared.repository.user;

import com.cyberlearnix.shared.entity.user.AuthAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuthAuditRepository extends JpaRepository<AuthAudit, String> {
    
    List<AuthAudit> findByEmailOrderByTimestampDesc(String email);
    
    List<AuthAudit> findByIpAddressOrderByTimestampDesc(String ipAddress);
    
    @Query("SELECT a FROM AuthAudit a WHERE a.email = :email AND a.action = 'LOGIN_FAILED' AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuthAudit> findFailedLoginAttempts(@Param("email") String email, @Param("since") LocalDateTime since);
    
    @Query("SELECT a FROM AuthAudit a WHERE a.ipAddress = :ipAddress AND a.action = 'LOGIN_FAILED' AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuthAudit> findFailedLoginAttemptsByIp(@Param("ipAddress") String ipAddress, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(a) FROM AuthAudit a WHERE a.email = :email AND a.action = 'LOGIN_SUCCESS' AND a.timestamp >= :since")
    long countSuccessfulLoginsSince(@Param("email") String email, @Param("since") LocalDateTime since);
}
