package com.cyberlearnix.shared.repository.user;

import com.cyberlearnix.shared.entity.user.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    // Used by existing per-user log endpoint (limited list, not paged)
    List<ActivityLog> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

    // Paged queries used by admin enriched log endpoint
    Page<ActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ActivityLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<ActivityLog> findByUserIdInOrderByCreatedAtDesc(List<String> userIds, Pageable pageable);

    Page<ActivityLog> findByEventTypeContainingIgnoreCaseOrderByCreatedAtDesc(String eventType, Pageable pageable);

    Page<ActivityLog> findByUserIdAndEventTypeContainingIgnoreCaseOrderByCreatedAtDesc(String userId, String eventType, Pageable pageable);

    Page<ActivityLog> findByUserIdInAndEventTypeContainingIgnoreCaseOrderByCreatedAtDesc(List<String> userIds, String eventType, Pageable pageable);

    Page<ActivityLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to, Pageable pageable);
}
