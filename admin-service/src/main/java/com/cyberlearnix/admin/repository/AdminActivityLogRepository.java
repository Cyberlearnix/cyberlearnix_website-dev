package com.cyberlearnix.admin.repository;

import com.cyberlearnix.admin.entity.AdminActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminActivityLogRepository extends JpaRepository<AdminActivityLog, Long> {
}
