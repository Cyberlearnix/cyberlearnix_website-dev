package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.ActivityLog;
import com.cyberlearnix.shared.repository.user.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/activity/logs")
public class ActivityLogController {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<List<ActivityLog>> getUserLogs(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(activityLogRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit)));
    }

    @PostMapping
    public ResponseEntity<ActivityLog> logActivity(@RequestBody ActivityLog activityLog) {
        return ResponseEntity.ok(activityLogRepository.save(activityLog));
    }
}
