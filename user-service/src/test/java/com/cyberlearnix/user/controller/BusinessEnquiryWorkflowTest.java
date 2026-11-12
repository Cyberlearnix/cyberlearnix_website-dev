package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.ActivityLog;
import com.cyberlearnix.shared.entity.user.BusinessEnquiry;
import com.cyberlearnix.shared.repository.user.ActivityLogRepository;
import com.cyberlearnix.shared.repository.user.BusinessEnquiryRepository;
import com.cyberlearnix.user.security.RateLimitingFilter;
import com.cyberlearnix.user.service.BusinessEnquiryEmailService;
import com.cyberlearnix.user.service.EmailNotificationService;
import com.cyberlearnix.user.service.OtpService;
import com.cyberlearnix.user.service.ResendService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusinessEnquiryWorkflowTest {

    @MockBean
    private OtpService otpService;

    @MockBean
    private EmailNotificationService emailNotificationService;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    private ResendService resendService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusinessEnquiryRepository enquiryRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private BusinessEnquiryEmailService businessEnquiryEmailService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String bearerFor(String userId, String role) {
        String token = Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.trim().getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }

    @BeforeEach
    void cleanDb() {
        activityLogRepository.deleteAll();
        enquiryRepository.deleteAll();
        reset(resendService);
        when(resendService.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);
    }

    private Map<String, Object> basePayload(String service, String category) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fullName", "Aarav Sharma");
        payload.put("organizationName", "Apex Tech Solutions");
        payload.put("email", "aarav.sharma@apextech.example");
        payload.put("mobileNumber", "+91 9876543210");
        payload.put("city", "Hyderabad");
        payload.put("designation", "CTO / Director");
        payload.put("enquiryType", category);
        payload.put("selectedServices", List.of(service));
        payload.put("requirements", "We need a scalable enterprise platform with modern architecture and security controls.");
        payload.put("estimatedBudget", "₹1 Lakh – ₹5 Lakhs");
        payload.put("expectedTimeline", "Within 15 Days");
        payload.put("preferredMeeting", "Online Meeting");
        payload.put("preferredContactMethod", "Email");
        payload.put("marketingSource", "LinkedIn");
        payload.put("privacyConsent", true);
        payload.put("contactConsent", true);
        payload.put("utmSource", "linkedin");
        payload.put("utmMedium", "paid_social");
        payload.put("utmCampaign", "enterprise_2026");
        payload.put("utmContent", "carousel_v1");
        return payload;
    }

    @Test
    void scenario1_websiteDevelopmentEnquiry_succeedsAndSendsAllFourEmails() throws Exception {
        Map<String, Object> payload = basePayload("Website Development", "IT & SOFTWARE");

        MvcResult res = mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.enquiryReference").exists())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        String ref = (String) body.get("enquiryReference");
        assertThat(ref).startsWith("CLX-ENQ-" + Year.now().getValue() + "-");

        // Wait for async email dispatch to complete
        verify(resendService, timeout(4000)).sendEmail(
                eq("connect@cyberlearnix.com"),
                contains(ref),
                contains("VIEW ENQUIRY IN ADMIN DASHBOARD")
        );
        verify(resendService, timeout(4000)).sendEmail(
                eq("cyberlearnix@gmail.com"),
                contains(ref),
                anyString()
        );
        verify(resendService, timeout(4000)).sendEmail(
                eq("palakurlashivakumar.179@gmail.com"),
                contains(ref),
                anyString()
        );
        verify(resendService, timeout(4000)).sendEmail(
                eq("aarav.sharma@apextech.example"),
                eq("Your Enquiry Has Been Received \u2013 Cyberlearnix Private Limited"),
                contains("Learn. Code. Innovate. Grow.")
        );

        BusinessEnquiry saved = enquiryRepository.findByEnquiryReference(ref).orElseThrow();
        assertThat(saved.getUtmSource()).isEqualTo("linkedin");
        assertThat(saved.getUtmCampaign()).isEqualTo("enterprise_2026");
        assertThat(saved.getPrivacyConsent()).isTrue();
        assertThat(saved.getContactConsent()).isTrue();
    }

    @Test
    void scenario2_4_5_cybersecurityCorporateAndInternshipEnquiries_succeed() throws Exception {
        // Cybersecurity service enquiry
        Map<String, Object> secPayload = basePayload("Vulnerability Assessment & Penetration Testing (VAPT)", "CYBERSECURITY");
        secPayload.put("email", "sec@finbank.example");
        mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enquiryType").value("CYBERSECURITY"));

        // Corporate training enquiry
        Map<String, Object> corpPayload = basePayload("Corporate Training", "EDUCATION & TRAINING");
        corpPayload.put("email", "hr@enterprise.example");
        mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enquiryType").value("EDUCATION & TRAINING"));

        // Internship partnership enquiry
        Map<String, Object> internPayload = basePayload("Internship / Apprenticeship Partnership", "EDUCATION & TRAINING");
        internPayload.put("email", "tpo@institute.example");
        mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(internPayload)))
                .andExpect(status().isCreated());

        assertThat(enquiryRepository.count()).isEqualTo(3);
    }

    @Test
    void scenario3_universityCollaborationEnquiry_storesAcademicFieldsAndValidatesInstitution() throws Exception {
        Map<String, Object> payload = basePayload("University / College Collaboration", "EDUCATION & TRAINING");
        payload.put("email", "principal@techuniv.edu");
        payload.put("designation", "Principal");
        payload.put("organizationName", "");
        payload.put("institutionName", "National Institute of Engineering");
        payload.put("institutionType", "Autonomous Engineering College");
        payload.put("department", "Computer Science & Cyber Security");
        payload.put("studentCount", "500+");
        payload.put("collaborationServices", List.of(
                "Cybersecurity Training",
                "Cybersecurity Lab Setup",
                "Memorandum of Understanding (MOU)"
        ));

        MvcResult res = mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        String ref = (String) body.get("enquiryReference");
        BusinessEnquiry saved = enquiryRepository.findByEnquiryReference(ref).orElseThrow();
        assertThat(saved.getOrganizationName()).isEqualTo("National Institute of Engineering");
        assertThat(saved.getInstitutionType()).isEqualTo("Autonomous Engineering College");
        assertThat(saved.getCollaborationServices()).containsExactly(
                "Cybersecurity Training",
                "Cybersecurity Lab Setup",
                "Memorandum of Understanding (MOU)"
        );

        // Missing institution name for University Collaboration should fail
        payload.put("institutionName", "");
        payload.put("organizationName", "");
        payload.put("email", "missing@techuniv.edu");
        mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Institution Name")));
    }

    @Test
    void scenario6_7_invalidEmailMissingFieldsAndUncheckedConsent_return400() throws Exception {
        // Invalid email
        Map<String, Object> badEmail = basePayload("Website Development", "IT & SOFTWARE");
        badEmail.put("email", "not-an-email");
        mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        // Missing mandatory fields + unchecked consents
        Map<String, Object> incomplete = Map.of(
                "fullName", "",
                "email", "valid@example.com",
                "privacyConsent", false,
                "contactConsent", false
        );
        mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incomplete)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.privacyConsent").exists())
                .andExpect(jsonPath("$.fieldErrors.contactConsent").exists());
    }

    @Test
    void scenario8_duplicateSubmissionWithinWindow_returns409Conflict() throws Exception {
        Map<String, Object> payload = basePayload("Custom Software Development", "IT & SOFTWARE");
        payload.put("email", "dup@company.example");

        mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.enquiryReference").exists());
    }

    @Test
    void scenario9_honeypotSpamRejected_andRateLimitingBlocksFlood() throws Exception {
        Map<String, Object> spam = basePayload("Cloud Solutions", "AI & CLOUD");
        spam.put("websiteUrl", "https://spam-bot.example");

        mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spam)))
                .andExpect(status().isBadRequest());

        assertThat(enquiryRepository.count()).isZero();

        // Direct RateLimitingFilter check with limit=2
        RateLimitingFilter filter = new RateLimitingFilter();
        ReflectionTestUtils.setField(filter, "maxAttempts", 5);
        ReflectionTestUtils.setField(filter, "enquiryMaxAttempts", 2);
        ReflectionTestUtils.setField(filter, "windowMinutes", 15);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/public/enquiries");
            req.setRemoteAddr("203.0.113.77");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/public/enquiries");
        blockedReq.setRemoteAddr("203.0.113.77");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, new MockFilterChain());
        assertThat(blockedRes.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void scenario10_emailFailureStillSavesEnquiryLogsFailureAndSupportsAdminRetry() throws Exception {
        when(resendService.sendEmail(anyString(), anyString(), anyString())).thenReturn(false);

        Map<String, Object> payload = basePayload("Security Audit", "CYBERSECURITY");
        payload.put("email", "audit@client.example");

        MvcResult res = mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        Long id = ((Number) body.get("id")).longValue();

        // Wait for async email attempt failure to record status
        Thread.sleep(350);
        BusinessEnquiry stored = enquiryRepository.findById(id).orElseThrow();
        assertThat(stored.getEmailNotificationStatus()).isEqualTo("FAILED");

        List<ActivityLog> logs = activityLogRepository.findAll();
        assertThat(logs).anyMatch(l -> "ENQUIRY_EMAIL_FAILURE".equals(l.getEventType()));

        // Now email provider recovers -> Admin retries email
        when(resendService.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/admin/enquiries/" + id + "/retry-email")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("admin-user", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotificationStatus").value("SENT"));
    }

    @Test
    void scenario11_adminAuthorizationSearchUpdateAuditAndExportsWork() throws Exception {
        Map<String, Object> payload = basePayload("AI Agent Development", "AI & CLOUD");
        payload.put("fullName", "<script>alert(1)</script>Vikram Rao");
        payload.put("email", "vikram@enterprise.example");
        payload.put("requirements", "=HYPERLINK(\"http://evil\",\"Click\") We want AI agent workflows.");

        MvcResult createRes = mockMvc.perform(post("/api/public/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = ((Number) objectMapper.readValue(createRes.getResponse().getContentAsString(), Map.class).get("id")).longValue();

        // Unauthenticated blocked
        mockMvc.perform(get("/api/admin/enquiries"))
                .andExpect(status().isUnauthorized());

        // Student role blocked
        mockMvc.perform(get("/api/admin/enquiries")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("student-1", "student")))
                .andExpect(status().isForbidden());

        // Admin search + stats
        mockMvc.perform(get("/api/admin/enquiries")
                        .param("search", "Vikram")
                        .param("category", "AI & CLOUD")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("admin-1", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Vikram Rao"))
                .andExpect(jsonPath("$.stats.totalEnquiries").value(1))
                .andExpect(jsonPath("$.stats.newEnquiries").value(1));

        // Update status + assignment + notes
        mockMvc.perform(patch("/api/admin/enquiries/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("admin-1", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "leadStatus", "Converted",
                                "assignedTo", "sales@cyberlearnix.com",
                                "adminNotes", "Signed MSA"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadStatus").value("Converted"))
                .andExpect(jsonPath("$.assignedTo").value("sales@cyberlearnix.com"));

        // Detail endpoint returns audit trail
        mockMvc.perform(get("/api/admin/enquiries/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("admin-1", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enquiry.leadStatus").value("Converted"))
                .andExpect(jsonPath("$.auditLogs").isArray());

        // CSV Export neutralizes formula injection
        MvcResult csvRes = mockMvc.perform(get("/api/admin/enquiries/export")
                        .param("format", "csv")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("admin-1", "admin")))
                .andExpect(status().isOk())
                .andReturn();
        String csvText = new String(csvRes.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csvText).contains("'=HYPERLINK");
        assertThat(csvText).contains("Vikram Rao");

        // Excel XLSX Export produces valid XSSFWorkbook
        MvcResult xlsxRes = mockMvc.perform(get("/api/admin/enquiries/export")
                        .param("format", "xlsx")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("admin-1", "admin")))
                .andExpect(status().isOk())
                .andReturn();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxRes.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheetAt(0).getRow(1).getCell(2).getStringCellValue()).isEqualTo("Vikram Rao");
        }
    }
}
