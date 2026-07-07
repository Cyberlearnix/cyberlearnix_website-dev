package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.dto.CreateMeetingRequest;
import com.cyberlearnix.attendance.dto.MeetingResponse;
import com.cyberlearnix.attendance.entity.Meeting;
import com.cyberlearnix.attendance.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public MeetingResponse createMeeting(CreateMeetingRequest request, String createdBy) {
        Meeting meeting = new Meeting();
        meeting.setTitle(request.getTitle());
        meeting.setDescription(request.getDescription());
        meeting.setCourseId(request.getCourseId());
        meeting.setFacultyId(request.getFacultyId());
        meeting.setStartTime(request.getStartTime());
        meeting.setEndTime(request.getEndTime());
        meeting.setCreatedBy(createdBy);
        meeting.setMeetingCode(generateMeetingCode());
        meeting.setStatus(Meeting.MeetingStatus.SCHEDULED);

        Meeting saved = meetingRepository.save(meeting);
        log.info("Created meeting with code: {}", saved.getMeetingCode());
        return mapToResponse(saved);
    }

    public List<MeetingResponse> getAllMeetings() {
        return meetingRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public MeetingResponse getMeetingById(String id) {
        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found with ID: " + id));
        return mapToResponse(meeting);
    }

    public Optional<Meeting> getMeetingEntityById(String id) {
        return meetingRepository.findById(id);
    }

    public Optional<Meeting> getMeetingEntityByCode(String code) {
        return meetingRepository.findByMeetingCode(code);
    }

    @Transactional
    public MeetingResponse updateMeeting(String id, CreateMeetingRequest request) {
        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found with ID: " + id));

        meeting.setTitle(request.getTitle());
        meeting.setDescription(request.getDescription());
        meeting.setCourseId(request.getCourseId());
        meeting.setFacultyId(request.getFacultyId());
        meeting.setStartTime(request.getStartTime());
        meeting.setEndTime(request.getEndTime());

        Meeting updated = meetingRepository.save(meeting);
        log.info("Updated meeting: {}", id);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteMeeting(String id) {
        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found with ID: " + id));
        meetingRepository.delete(meeting);
        log.info("Deleted meeting: {}", id);
    }

    @Transactional
    public void cancelMeeting(String id) {
        Meeting meeting = meetingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found with ID: " + id));
        meeting.setStatus(Meeting.MeetingStatus.CANCELLED);
        meetingRepository.save(meeting);
        log.info("Cancelled meeting: {}", id);
    }

    public List<MeetingResponse> getMeetingsByCourse(Long courseId) {
        return meetingRepository.findByCourseId(courseId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<MeetingResponse> getMeetingsByCourses(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        return meetingRepository.findByCourseIds(courseIds).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<MeetingResponse> getLiveMeetings() {
        return meetingRepository.findLiveMeetings().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<MeetingResponse> getUpcomingMeetings() {
        LocalDateTime from = LocalDateTime.now();
        LocalDateTime to = from.plusHours(24);
        return meetingRepository.findUpcomingMeetings(from, to).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private String generateMeetingCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return String.format("cyberlearnix-%d-%s", Instant.now().getEpochSecond(), sb.toString());
    }

    public MeetingResponse mapToResponse(Meeting meeting) {
        MeetingResponse response = new MeetingResponse();
        response.setId(meeting.getId());
        response.setTitle(meeting.getTitle());
        response.setDescription(meeting.getDescription());
        response.setMeetingCode(meeting.getMeetingCode());
        response.setCourseId(meeting.getCourseId());
        response.setFacultyId(meeting.getFacultyId());
        response.setStartTime(meeting.getStartTime());
        response.setEndTime(meeting.getEndTime());
        response.setStatus(meeting.getStatus().name());
        response.setCreatedBy(meeting.getCreatedBy());
        response.setJoinUrl("https://meet.jit.si/" + meeting.getMeetingCode());
        response.setCreatedAt(meeting.getCreatedAt());
        response.setUpdatedAt(meeting.getUpdatedAt());
        return response;
    }
}
