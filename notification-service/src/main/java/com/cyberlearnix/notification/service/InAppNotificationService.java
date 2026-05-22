package com.cyberlearnix.notification.service;

import com.cyberlearnix.notification.dto.CreateInboxNotificationRequest;
import com.cyberlearnix.notification.dto.InboxNotificationDTO;
import com.cyberlearnix.notification.entity.InAppNotification;
import com.cyberlearnix.notification.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationService {

    private final InAppNotificationRepository repository;
    private final RestTemplate restTemplate;
    private final NotificationSseEmitterRegistry sseRegistry;

    @Value("${services.enrollment-service.url:http://localhost:8083}")
    private String enrollmentServiceUrl;

    @Value("${services.user-service.url:http://localhost:8081}")
    private String userServiceUrl;

    // ─── Inbox queries ────────────────────────────────────────────────────────

    public List<InboxNotificationDTO> getInbox(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(String userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    // ─── Mutation ─────────────────────────────────────────────────────────────

    @Transactional
    public InboxNotificationDTO markRead(Long id, String userId) {
        InAppNotification notif = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notif.setRead(true);
        return toDTO(repository.save(notif));
    }

    @Transactional
    public int markAllRead(String userId) {
        return repository.markAllReadByUserId(userId);
    }

    @Transactional
    public void delete(Long id, String userId) {
        repository.findByIdAndUserId(id, userId)
                .ifPresent(repository::delete);
    }

    @Transactional
    public void clearAll(String userId) {
        repository.deleteAllByUserId(userId);
    }

    // ─── Creation helpers ─────────────────────────────────────────────────────

    public InAppNotification createForUser(String userId, String type, String title, String body, String link) {
        InAppNotification n = InAppNotification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .link(link)
                .createdAt(LocalDateTime.now())
                .build();
        InAppNotification saved = repository.save(n);
        sseRegistry.push(userId, toDTO(saved));
        return saved;
    }

    public List<InAppNotification> createBatch(List<String> userIds, String type, String title, String body, String link) {
        List<InAppNotification> batch = userIds.stream()
                .map(uid -> InAppNotification.builder()
                        .userId(uid)
                        .type(type)
                        .title(title)
                        .body(body)
                        .link(link)
                        .createdAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());
        List<InAppNotification> saved = repository.saveAll(batch);
        saved.forEach(n -> sseRegistry.push(n.getUserId(), toDTO(n)));
        return saved;
    }

    // ─── Orchestrated creation (resolves recipients from other services) ──────

    @Transactional
    public List<InboxNotificationDTO> processRequest(CreateInboxNotificationRequest req) {
        List<String> userIds;

        if (req.getUserIds() != null && !req.getUserIds().isEmpty()) {
            // Explicit user list
            userIds = req.getUserIds();
        } else if (req.getUserId() != null && !req.getUserId().isBlank()) {
            // Single user
            userIds = List.of(req.getUserId());
        } else if (req.getCourseId() != null) {
            // Resolve enrolled students for a course
            userIds = resolveEnrolledStudents(req.getCourseId());
        } else if (req.getTargetRole() != null && !req.getTargetRole().isBlank()) {
            // Resolve all users with this role
            userIds = resolveUsersByRole(req.getTargetRole());
        } else {
            throw new IllegalArgumentException("Must provide userId, userIds, courseId, or targetRole");
        }

        if (userIds.isEmpty()) {
            log.warn("No recipients resolved for notification '{}' (type={})", req.getTitle(), req.getType());
            return Collections.emptyList();
        }

        List<InAppNotification> created = createBatch(userIds, req.getType(), req.getTitle(), req.getBody(), req.getLink());
        log.info("Created {} in-app notification(s) [type={}, title='{}']", created.size(), req.getType(), req.getTitle());

        return created.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ─── Admin operations ─────────────────────────────────────────────────────

    public List<InboxNotificationDTO> getAdminHistory() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .limit(500) // safety cap
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public int recallNotification(Long id) {
        // Find the notification to get type+title for broadcast recall
        InAppNotification n = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        LocalDateTime batchWindow = n.getCreatedAt().minusSeconds(5);
        int deleted = repository.recallBroadcast(n.getType(), n.getTitle(), batchWindow);
        log.info("Recalled {} notification row(s) for type={} title='{}'", deleted, n.getType(), n.getTitle());
        return deleted;
    }

    // ─── Cross-service resolution ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String> resolveEnrolledStudents(Long courseId) {
        try {
            String url = enrollmentServiceUrl + "/api/enrollments/students?courseId=" + courseId;
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            if (resp.getBody() == null) return Collections.emptyList();
            return resp.getBody().stream()
                    .map(m -> String.valueOf(m.get("studentId")))
                    .filter(sid -> sid != null && !sid.isBlank() && !"null".equals(sid))
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to resolve enrolled students for courseId={}: {}", courseId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveUsersByRole(String role) {
        try {
            String url = userServiceUrl + "/api/users?role=" + role.toLowerCase();
            ResponseEntity<List<Map<String, Object>>> resp = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            if (resp.getBody() == null) return Collections.emptyList();
            return resp.getBody().stream()
                    .map(m -> String.valueOf(m.get("id")))
                    .filter(uid -> uid != null && !uid.isBlank() && !"null".equals(uid))
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to resolve users for role={}: {}", role, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    public InboxNotificationDTO toDTO(InAppNotification n) {
        return InboxNotificationDTO.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .link(n.getLink())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
