package com.cyberlearnix.attendance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Zoho Meeting webhook event payload.
 * Matches Zoho's webhook JSON structure.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZohoWebhookEvent {

    @JsonProperty("event")
    private String event; // meeting_started | meeting_ended | participant_joined | participant_left | participant_rejoined

    @JsonProperty("payload")
    private ZohoPayload payload;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZohoPayload {

        @JsonProperty("meeting_id")
        private String meetingId;

        @JsonProperty("meeting_key")
        private String meetingKey;

        @JsonProperty("topic")
        private String topic;

        @JsonProperty("start_time")
        private String startTime;

        @JsonProperty("end_time")
        private String endTime;

        @JsonProperty("duration")
        private Integer duration;

        @JsonProperty("host_email")
        private String hostEmail;

        @JsonProperty("participant")
        private ZohoParticipant participant;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ZohoParticipant {

        @JsonProperty("participant_id")
        private String participantId;

        @JsonProperty("user_id")
        private String userId;

        @JsonProperty("email")
        private String email;

        @JsonProperty("name")
        private String name;

        @JsonProperty("join_time")
        private String joinTime;

        @JsonProperty("leave_time")
        private String leaveTime;

        @JsonProperty("duration")
        private Integer duration;

        @JsonProperty("ip_address")
        private String ipAddress;

        @JsonProperty("user_agent")
        private String userAgent;

        @JsonProperty("device")
        private String device;

        @JsonProperty("browser")
        private String browser;
    }
}
