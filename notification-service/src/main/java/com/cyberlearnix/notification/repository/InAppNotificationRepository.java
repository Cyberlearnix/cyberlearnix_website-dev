package com.cyberlearnix.notification.repository;

import com.cyberlearnix.notification.entity.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    List<InAppNotification> findByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserIdAndReadFalse(String userId);

    Optional<InAppNotification> findByIdAndUserId(Long id, String userId);

    @Modifying
    @Transactional
    @Query("UPDATE InAppNotification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    int markAllReadByUserId(@Param("userId") String userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM InAppNotification n WHERE n.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);

    // Admin: all notifications ordered newest first
    List<InAppNotification> findAllByOrderByCreatedAtDesc();

    // Admin: all for a specific type (used for history filtering)
    List<InAppNotification> findByTypeOrderByCreatedAtDesc(String type);

    // Admin: recall — delete all rows with same title+type created in batch (same broadcast)
    @Modifying
    @Transactional
    @Query("DELETE FROM InAppNotification n WHERE n.type = :type AND n.title = :title AND n.createdAt >= :since")
    int recallBroadcast(@Param("type") String type, @Param("title") String title, @Param("since") LocalDateTime since);
}
