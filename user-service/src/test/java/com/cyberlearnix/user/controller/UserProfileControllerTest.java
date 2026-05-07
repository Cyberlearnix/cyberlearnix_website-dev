package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.user.TeacherPermissionRepository;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import com.cyberlearnix.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for the profile GET and PUT endpoints on {@link UserController}.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock private UserService userService;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private TeacherPermissionRepository teacherPermissionRepository;

    @InjectMocks
    private UserController controller;

    private MockMvc mockMvc;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        mapper = new ObjectMapper();
    }

    // ── helper ────────────────────────────────────────────────────────
    private UserProfile profile(String userId) {
        UserProfile p = new UserProfile();
        p.setId(userId);
        p.setFullName("Old Name");
        return p;
    }

    private Authentication auth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
    }

    // ── GET /api/users/profile ──────────────────────────────────────

    @Test
    void getProfile_existingUser_returns200() throws Exception {
        when(userProfileRepository.findById("u1")).thenReturn(Optional.of(profile("u1")));

        mockMvc.perform(get("/api/users/profile")
                        .principal(auth("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u1"));
    }

    @Test
    void getProfile_nonExistentUser_returns404() throws Exception {
        when(userProfileRepository.findById("u99")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/profile")
                        .principal(auth("u99")))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/users/profile ──────────────────────────────────────

    @Test
    void updateProfile_validFields_returns200WithUpdatedData() throws Exception {
        UserProfile existing = profile("u1");
        when(userProfileRepository.findById("u1")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of(
                "fullName", "Jane Doe",
                "phone", "+91 9000000001",
                "bio", "Security enthusiast"
        );

        mockMvc.perform(put("/api/users/profile")
                        .principal(auth("u1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.phone").value("+91 9000000001"))
                .andExpect(jsonPath("$.bio").value("Security enthusiast"));

        verify(userProfileRepository).save(existing);
    }

    @Test
    void updateProfile_missingProfileRecord_returns404() throws Exception {
        when(userProfileRepository.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/users/profile")
                        .principal(auth("ghost"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"No One\"}"))
                .andExpect(status().isNotFound());

        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void updateProfile_onlyProvidedFieldsAreUpdated() throws Exception {
        UserProfile existing = profile("u2");
        existing.setPhone("+91 8888888888");
        when(userProfileRepository.findById("u2")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Only update bio — phone should remain unchanged
        Map<String, Object> body = Map.of("bio", "New bio");

        mockMvc.perform(put("/api/users/profile")
                        .principal(auth("u2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("New bio"))
                .andExpect(jsonPath("$.phone").value("+91 8888888888"));
    }

    @Test
    void updateProfile_ageFieldAcceptsNumericValue() throws Exception {
        UserProfile existing = profile("u3");
        when(userProfileRepository.findById("u3")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of("age", 28);

        mockMvc.perform(put("/api/users/profile")
                        .principal(auth("u3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").value(28));
    }
}
