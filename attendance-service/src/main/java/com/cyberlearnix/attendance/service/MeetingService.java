package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.dto.CreateMeetingRequest;
import com.cyberlearnix.attendance.dto.MeetingDto;
import com.cyberlearnix.attendance.entity.Meeting;
import com.cyberlearnix.attendance.repository.MeetingRepository;
import com.cyberlearnix.attendance.repository.MeetingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepo;
    private final MeetingSessionRepository sessionRepo;
    private final ZohoMeetingApiClient zohoClient;

    @Transactional
    public MeetingDto createMeeting(CreateMeetingRequest req, String hostUserId, String hostName) {
        Meeting meeting = new Meeting();
        meeting.setTitle(req.getTitle());
        meeting.setDescription(req.getDescription());
        meeting.setScheduledStart(req.getScheduledStart());
        meeting.setScheduledEnd(req.getScheduledEnd());
        meeting.setHostUserId(hostUserId);
        meeting.setHostName(hostName);
        meeting.setCourseId(req.getCourseId());
        meeting.setBatchId(req.getBatchId());
        meeting.setMandatory(req.getMandatory());
        meeting.setNotes(req.getNotes());
        meeting.setStatus(Meeting.MeetingStatus.SCHEDULED);

        // Create in Zoho if requested
        if (Boolean.TRUE.equals(req.getCreateInZoho())) {
            try {
                ZohoMeetingApiClient.ZohoMeetingResponse zohoResp = zohoClient.createMeeting(meeting);
                if (zohoResp != null) {
                    meeting.setZohoMeetingId(zohoResp.getMeetingId());
                    meeting.setZohoJoinUrl(zohoResp.getJoinUrl());
                    meeting.setMeetingPassword(zohoResp.getPassword());
                }
            } catch (Exception e) {
                log.error("Failed to create meeting in Zoho: {}", e.getMessage());
            }
        }

        meeting = meetingRepo.save(meeting);
        return toDto(meeting, null);
    }

    public MeetingDto getMeeting(String id) {
        Meeting meeting = meetingRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + id));
        long live = sessionRepo.findActiveLiveParticipants(id).size();
        return toDto(meeting, live);
    }

    public Page<MeetingDto> getMeetingsByCourse(String courseId, Pageable pageable) {
        return meetingRepo.findByCourseIdPaged(courseId, pageable)
            .map(m -> toDto(m, null));
    }

    public List<MeetingDto> getLiveMeetings() {
        return meetingRepo.findLiveMeetings().stream()
            .map(m -> {
                long live = sessionRepo.findActiveLiveParticipants(m.getId()).size();
                return toDto(m, live);
            })
            .collect(Collectors.toList());
    }

    public List<MeetingDto> getUpcomingMeetings(int hours) {
        LocalDateTime from = LocalDateTime.now();
        LocalDateTime to = from.plusHours(hours);
        return meetingRepo.findUpcomingMeetings(from, to).stream()
            .map(m -> toDto(m, null))
            .collect(Collectors.toList());
    }

    @Transactional
    public MeetingDto updateMeeting(String id, CreateMeetingRequest req) {
        Meeting meeting = meetingRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + id));
        if (req.getTitle() != null) meeting.setTitle(req.getTitle());
        if (req.getDescription() != null) meeting.setDescription(req.getDescription());
        if (req.getScheduledStart() != null) meeting.setScheduledStart(req.getScheduledStart());
        if (req.getScheduledEnd() != null) meeting.setScheduledEnd(req.getScheduledEnd());
        if (req.getMandatory() != null) meeting.setMandatory(req.getMandatory());
        if (req.getNotes() != null) meeting.setNotes(req.getNotes());
        return toDto(meetingRepo.save(meeting), null);
    }

    @Transactional
    public void cancelMeeting(String id) {
        Meeting meeting = meetingRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + id));
        meeting.setStatus(Meeting.MeetingStatus.CANCELLED);
        meetingRepo.save(meeting);
    }

    public MeetingDto toDto(Meeting m, Long liveCount) {
        MeetingDto dto = new MeetingDto();
        dto.setId(m.getId());
        dto.setZohoMeetingId(m.getZohoMeetingId());
        dto.setTitle(m.getTitle());
        dto.setDescription(m.getDescription());
        dto.setScheduledStart(m.getScheduledStart());
        dto.setScheduledEnd(m.getScheduledEnd());
        dto.setActualStart(m.getActualStart());
        dto.setActualEnd(m.getActualEnd());
        dto.setDurationMinutes(m.getDurationMinutes());
        dto.setHostUserId(m.getHostUserId());
        dto.setHostName(m.getHostName());
        dto.setCourseId(m.getCourseId());
        dto.setBatchId(m.getBatchId());
        dto.setMeetingUrl(m.getMeetingUrl());
        dto.setZohoJoinUrl(m.getZohoJoinUrl());
        dto.setStatus(m.getStatus());
        dto.setAttendanceFinalized(m.getAttendanceFinalized());
        dto.setMandatory(m.getMandatory());
        dto.setNotes(m.getNotes());
        dto.setLiveParticipantCount(liveCount);
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }
}
