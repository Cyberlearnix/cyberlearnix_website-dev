package com.cyberlearnix.lab.service;

import com.cyberlearnix.lab.entity.CourseLabConfig;
import com.cyberlearnix.lab.entity.SetupStatus;
import com.cyberlearnix.lab.repository.CourseLabConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabImageBuildService {

    private final CourseLabConfigRepository courseLabConfigRepository;
    private final DockerClientService dockerClientService;

    /** In-memory log buffers for live SSE streaming (keyed by courseId). */
    private final ConcurrentHashMap<Long, StringBuilder> liveLogs = new ConcurrentHashMap<>();
    /** Active SSE emitters (keyed by courseId). */
    private final ConcurrentHashMap<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // ── Script management ─────────────────────────────────────────────────────

    public CourseLabConfig saveSetupScript(Long courseId, String script) {
        CourseLabConfig config = courseLabConfigRepository.findByCourseId(courseId)
                .orElseThrow(() -> new EntityNotFoundException("No lab config for course: " + courseId));
        config.setSetupScript(script);
        if (config.getSetupStatus() == null || config.getSetupStatus() == SetupStatus.NOT_CONFIGURED) {
            config.setSetupStatus(SetupStatus.NOT_CONFIGURED);
        }
        return courseLabConfigRepository.save(config);
    }

    // ── Async build ───────────────────────────────────────────────────────────

    @Async
    public void triggerBuild(Long courseId) {
        CourseLabConfig config = courseLabConfigRepository.findByCourseId(courseId)
                .orElseThrow(() -> new EntityNotFoundException("No lab config for course: " + courseId));

        if (config.getSetupScript() == null || config.getSetupScript().isBlank()) {
            emitLog(courseId, "ERROR: No setup script configured.\n");
            config.setSetupStatus(SetupStatus.FAILED);
            courseLabConfigRepository.save(config);
            completeLiveLogs(courseId);
            return;
        }

        config.setSetupStatus(SetupStatus.BUILDING);
        courseLabConfigRepository.save(config);

        String baseImage       = config.getLabTemplate().getDockerImage();
        String stagedImageTag  = "cyberlearnix-course-" + courseId + "-staged:latest";
        String tempContainerName = "cyberlearnix-setup-" + courseId + "-" + System.currentTimeMillis();
        String containerId = null;
        StringBuilder fullLog = new StringBuilder();

        try {
            emitLog(courseId, "=== Starting lab image build for course " + courseId + " ===\n");
            emitLog(courseId, "Base image: " + baseImage + "\n");
            emitLog(courseId, "Staged image: " + stagedImageTag + "\n\n");

            emitLog(courseId, "Creating setup container...\n");
            containerId = dockerClientService.createSetupContainer(baseImage, tempContainerName);

            emitLog(courseId, "Starting container...\n");
            dockerClientService.startContainer(containerId);

            emitLog(courseId, "\n--- Running setup script ---\n");
            String scriptOutput = dockerClientService.execScript(containerId, config.getSetupScript(), 600);
            emitLog(courseId, scriptOutput);
            fullLog.append(scriptOutput);

            emitLog(courseId, "\n--- Stopping container ---\n");
            dockerClientService.stopContainer(containerId);

            emitLog(courseId, "Committing as: " + stagedImageTag + "\n");
            dockerClientService.commitContainer(containerId, stagedImageTag);

            dockerClientService.removeContainer(containerId);
            containerId = null;

            // Reload from DB to avoid stale-state overwrite
            config = courseLabConfigRepository.findByCourseId(courseId).orElseThrow();
            config.setStagedDockerImage(stagedImageTag);
            config.setSetupStatus(SetupStatus.STAGED);
            config.setSetupLog(fullLog.toString());
            courseLabConfigRepository.save(config);

            emitLog(courseId, "\n=== BUILD SUCCESSFUL — image staged ===\n");
            emitLog(courseId, "Click 'Publish' to make this available to students.\n");

        } catch (Exception e) {
            log.error("Lab image build failed for course {}: {}", courseId, e.getMessage(), e);
            emitLog(courseId, "\n=== BUILD FAILED: " + e.getMessage() + " ===\n");

            // DO NOT touch activeDockerImage — students keep using previous good image
            config = courseLabConfigRepository.findByCourseId(courseId).orElseThrow();
            config.setSetupStatus(SetupStatus.FAILED);
            config.setSetupLog(fullLog + "\nBUILD FAILED: " + e.getMessage());
            courseLabConfigRepository.save(config);

            // Cleanup temp container if left running
            if (containerId != null) {
                try { dockerClientService.removeContainer(containerId); } catch (Exception ignored) {}
            }
        } finally {
            completeLiveLogs(courseId);
        }
    }

    // ── Publish ───────────────────────────────────────────────────────────────

    public CourseLabConfig publishStagedImage(Long courseId) {
        CourseLabConfig config = courseLabConfigRepository.findByCourseId(courseId)
                .orElseThrow(() -> new EntityNotFoundException("No lab config for course: " + courseId));
        if (config.getStagedDockerImage() == null) {
            throw new IllegalStateException("No staged image to publish for course: " + courseId);
        }
        config.setActiveDockerImage(config.getStagedDockerImage());
        config.setSetupStatus(SetupStatus.ACTIVE);
        return courseLabConfigRepository.save(config);
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public Map<String, Object> getBuildStatus(Long courseId) {
        CourseLabConfig config = courseLabConfigRepository.findByCourseId(courseId)
                .orElseThrow(() -> new EntityNotFoundException("No lab config for course: " + courseId));
        return Map.of(
                "status",       config.getSetupStatus() != null ? config.getSetupStatus().name() : "NOT_CONFIGURED",
                "stagedImage",  config.getStagedDockerImage()  != null ? config.getStagedDockerImage()  : "",
                "activeImage",  config.getActiveDockerImage()  != null ? config.getActiveDockerImage()  : "",
                "log",          config.getSetupLog()           != null ? config.getSetupLog()           : ""
        );
    }

    // ── SSE streaming ─────────────────────────────────────────────────────────

    public SseEmitter createLogEmitter(Long courseId) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10-minute timeout
        emitters.put(courseId, emitter);

        // Replay any already-captured content (handles reconnect case)
        StringBuilder existing = liveLogs.get(courseId);
        if (existing != null && existing.length() > 0) {
            try {
                emitter.send(SseEmitter.event().data(existing.toString()));
            } catch (Exception ignored) {}
        }

        emitter.onCompletion(() -> emitters.remove(courseId, emitter));
        emitter.onTimeout(() -> { emitter.complete(); emitters.remove(courseId, emitter); });
        return emitter;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void emitLog(Long courseId, String message) {
        liveLogs.computeIfAbsent(courseId, k -> new StringBuilder()).append(message);
        SseEmitter emitter = emitters.get(courseId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(message));
            } catch (Exception e) {
                emitters.remove(courseId, emitter);
            }
        }
    }

    private void completeLiveLogs(Long courseId) {
        SseEmitter emitter = emitters.remove(courseId);
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
        // liveLogs entry is retained briefly for SSE reconnect replays; GC handles cleanup.
    }
}
