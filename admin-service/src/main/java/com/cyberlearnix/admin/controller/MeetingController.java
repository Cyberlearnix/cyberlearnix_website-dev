package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.dto.CreateMeetingRequest;
import com.cyberlearnix.admin.dto.MeetingResponse;
import com.cyberlearnix.admin.entity.Meeting;
import com.cyberlearnix.admin.repository.MeetingRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/meetings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DUAL', 'INSTITUTE')")
public class MeetingController {

    private final MeetingRepository meetingRepository;

    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ── Create ─────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<MeetingResponse> create(
            @Valid @RequestBody CreateMeetingRequest req,
            @RequestHeader(value = "X-User-Id", defaultValue = "admin") String userId) {

        Meeting m = new Meeting();
        m.setTitle(req.getSubject());
        m.setDescription(req.getDescription());
        m.setCourseId(req.getCourseId());
        m.setFacultyId(userId);
        m.setStartTime(req.getStartDateTime());
        m.setEndTime(req.getEndDateTime());
        m.setCreatedBy(userId);
        m.setStatus(Meeting.MeetingStatus.SCHEDULED);
        String code = generateCode();
        m.setMeetingCode(code);
        m.setJoinUrl("https://meet.jit.si/" + code);

        Meeting saved = meetingRepository.save(m);
        log.info("[Meetings] Created meeting {} by {}", saved.getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    // ── List all ───────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<MeetingResponse>> list() {
        List<MeetingResponse> meetings = meetingRepository.findAllByOrderByStartTimeDesc()
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(meetings);
    }

    // ── Get by ID ──────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getById(@PathVariable String id) {
        Meeting m = meetingRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Meeting not found: " + id));
        return ResponseEntity.ok(toResponse(m));
    }

    // ── Update ─────────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<MeetingResponse> update(
            @PathVariable String id,
            @Valid @RequestBody CreateMeetingRequest req,
            @RequestHeader(value = "X-User-Id", defaultValue = "admin") String userId) {

        Meeting m = meetingRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Meeting not found: " + id));

        m.setTitle(req.getSubject());
        m.setDescription(req.getDescription());
        m.setCourseId(req.getCourseId());
        m.setFacultyId(userId);
        m.setStartTime(req.getStartDateTime());
        m.setEndTime(req.getEndDateTime());

        Meeting saved = meetingRepository.save(m);
        log.info("[Meetings] Updated meeting {} by {}", id, userId);
        return ResponseEntity.ok(toResponse(saved));
    }

    // ── Delete ─────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        Meeting m = meetingRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Meeting not found: " + id));
        meetingRepository.delete(m);
        log.info("[Meetings] Deleted meeting {}", id);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return "cl-" + Instant.now().getEpochSecond() + "-" + sb;
    }

    private MeetingResponse toResponse(Meeting m) {
        MeetingResponse r = new MeetingResponse();
        r.setId(m.getId());
        r.setTitle(m.getTitle());
        r.setDescription(m.getDescription());
        r.setMeetingCode(m.getMeetingCode());
        r.setCourseId(m.getCourseId());
        r.setFacultyId(m.getFacultyId());
        r.setStartTime(m.getStartTime());
        r.setEndTime(m.getEndTime());
        r.setStatus(m.getStatus() != null ? m.getStatus().name() : "SCHEDULED");
        r.setJoinUrl(m.getJoinUrl() != null ? m.getJoinUrl() : "https://meet.jit.si/" + m.getMeetingCode());
        r.setCreatedBy(m.getCreatedBy());
        r.setCreatedAt(m.getCreatedAt());
        r.setUpdatedAt(m.getUpdatedAt());
        return r;
    }
}
