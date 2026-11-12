package com.cyberlearnix.user.service;

import com.cyberlearnix.shared.entity.identity.Member;
import com.cyberlearnix.shared.entity.user.ActivityLog;
import com.cyberlearnix.shared.entity.user.BusinessEnquiry;
import com.cyberlearnix.shared.entity.user.User;
import com.cyberlearnix.shared.repository.identity.MemberRepository;
import com.cyberlearnix.shared.repository.user.ActivityLogRepository;
import com.cyberlearnix.shared.repository.user.BusinessEnquiryRepository;
import com.cyberlearnix.shared.repository.user.UserRepository;
import com.cyberlearnix.user.dto.BusinessEnquiryRequest;
import com.cyberlearnix.user.dto.BusinessEnquiryUpdateRequest;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BusinessEnquiryService {

    public static final List<String> ALLOWED_LEAD_STATUSES = List.of(
            "New",
            "Contacted",
            "Meeting Scheduled",
            "Proposal Sent",
            "Negotiation",
            "Converted",
            "Lost"
    );

    private static final Map<String, String> SERVICE_TO_CATEGORY = buildServiceCategoryMap();
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("(?is)<\\s*script.*?>.*?<\\s*/\\s*script\\s*>");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile("(?i)\\bon\\w+\\s*=");
    private static final DateTimeFormatter EXPORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BusinessEnquiryRepository enquiryRepository;
    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final TurnstileVerificationService turnstileVerificationService;
    private final BusinessEnquiryEmailService emailService;
    private final ReentrantLock referenceLock = new ReentrantLock();

    public BusinessEnquiryService(
            BusinessEnquiryRepository enquiryRepository,
            ActivityLogRepository activityLogRepository,
            UserRepository userRepository,
            MemberRepository memberRepository,
            TurnstileVerificationService turnstileVerificationService,
            BusinessEnquiryEmailService emailService) {
        this.enquiryRepository = enquiryRepository;
        this.activityLogRepository = activityLogRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.turnstileVerificationService = turnstileVerificationService;
        this.emailService = emailService;
    }

    public BusinessEnquiry submitEnquiry(BusinessEnquiryRequest request, String clientIp) {
        // 1. Honeypot anti-spam check
        if (request.getWebsiteUrl() != null && !request.getWebsiteUrl().isBlank()) {
            log.warn("Honeypot triggered on business enquiry from IP {}", clientIp);
            throw new IllegalArgumentException("Spam detected. Submission rejected.");
        }

        // 2. Cloudflare Turnstile CAPTCHA verification
        if (!turnstileVerificationService.verify(request.getCaptchaToken(), clientIp)) {
            throw new IllegalArgumentException("Security verification (CAPTCHA) failed. Please refresh and try again.");
        }

        // 3. Clean and validate inputs
        String fullName = cleanText(request.getFullName(), false);
        String email = cleanText(request.getEmail(), false).toLowerCase(Locale.ROOT);
        String mobileNumber = cleanText(request.getMobileNumber(), false);
        String city = cleanText(request.getCity(), false);
        String requirements = cleanText(request.getRequirements(), true);

        if (fullName.isBlank() || email.isBlank() || mobileNumber.isBlank() || city.isBlank() || requirements.isBlank()) {
            throw new IllegalArgumentException("Required fields contain invalid characters or are blank.");
        }

        List<String> selectedServices = request.getSelectedServices().stream()
                .map(s -> cleanText(s, false))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        if (selectedServices.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one service or enquiry option.");
        }

        boolean isUniversityCollab = selectedServices.stream()
                .anyMatch(s -> s.equalsIgnoreCase("University / College Collaboration"));

        String organizationName = cleanNullable(request.getOrganizationName(), false);
        String institutionName = cleanNullable(request.getInstitutionName(), false);
        String resolvedOrganization = (institutionName != null && !institutionName.isBlank())
                ? institutionName
                : organizationName;

        if (isUniversityCollab && (resolvedOrganization == null || resolvedOrganization.isBlank())) {
            throw new IllegalArgumentException("Institution Name is required when selecting University / College Collaboration.");
        }

        List<String> collaborationServices = request.getCollaborationServices() == null
                ? new ArrayList<>()
                : request.getCollaborationServices().stream()
                        .map(s -> cleanText(s, false))
                        .filter(s -> !s.isBlank())
                        .distinct()
                        .toList();

        // 4. Duplicate submission prevention (10-minute cooldown for identical payload)
        String submissionHash = computeSubmissionHash(email, selectedServices, requirements);
        Optional<BusinessEnquiry> duplicate = enquiryRepository
                .findTopBySubmissionHashAndCreatedAtAfterOrderByCreatedAtDesc(
                        submissionHash, LocalDateTime.now().minusMinutes(10));
        if (duplicate.isPresent()) {
            BusinessEnquiry existing = duplicate.get();
            throw new DuplicateEnquiryException(
                    "Duplicate submission detected. Your previous enquiry (" + existing.getEnquiryReference() + ") has already been received.",
                    existing.getEnquiryReference()
            );
        }

        // 5. Build entity
        BusinessEnquiry entity = new BusinessEnquiry();
        entity.setFullName(fullName);
        entity.setOrganizationName(resolvedOrganization);
        entity.setEmail(email);
        entity.setMobileNumber(mobileNumber);
        entity.setCity(city);
        entity.setDesignation(cleanNullable(request.getDesignation(), false));
        entity.setEnquiryType(resolveEnquiryType(request.getEnquiryType(), selectedServices));
        entity.setSelectedServices(new ArrayList<>(selectedServices));
        entity.setRequirements(requirements);
        entity.setEstimatedBudget(cleanNullable(request.getEstimatedBudget(), false));
        entity.setExpectedTimeline(cleanNullable(request.getExpectedTimeline(), false));
        entity.setPreferredMeeting(cleanNullable(request.getPreferredMeeting(), false));

        if (isUniversityCollab) {
            entity.setInstitutionType(cleanNullable(request.getInstitutionType(), false));
            entity.setDepartment(cleanNullable(request.getDepartment(), false));
            entity.setStudentCount(cleanNullable(request.getStudentCount(), false));
            entity.setCollaborationServices(new ArrayList<>(collaborationServices));
        } else {
            entity.setCollaborationServices(new ArrayList<>());
        }

        entity.setPreferredContactMethod(cleanNullable(request.getPreferredContactMethod(), false));
        entity.setMarketingSource(cleanNullable(request.getMarketingSource(), false));
        entity.setPrivacyConsent(Boolean.TRUE.equals(request.getPrivacyConsent()));
        entity.setContactConsent(Boolean.TRUE.equals(request.getContactConsent()));

        entity.setUtmSource(cleanNullable(request.getUtmSource(), false));
        entity.setUtmMedium(cleanNullable(request.getUtmMedium(), false));
        entity.setUtmCampaign(cleanNullable(request.getUtmCampaign(), false));
        entity.setUtmContent(cleanNullable(request.getUtmContent(), false));

        entity.setSubmissionHash(submissionHash);
        entity.setSourceIp(clientIp);
        entity.setLeadStatus("New");
        entity.setEmailNotificationStatus("PENDING");

        BusinessEnquiry saved = saveWithUniqueReference(entity);

        // 6. Trigger async email notifications (enquiry stays saved even if email delivery fails)
        emailService.sendEnquiryEmailsAsync(saved.getId());

        return saved;
    }

    private BusinessEnquiry saveWithUniqueReference(BusinessEnquiry entity) {
        referenceLock.lock();
        try {
            String year = String.valueOf(Year.now().getValue());
            String prefix = "CLX-ENQ-" + year + "-";
            long count = enquiryRepository.countByReferencePrefix(prefix);
            long seq = count + 1;

            for (int attempt = 0; attempt < 25; attempt++) {
                String candidate = prefix + String.format("%05d", seq + attempt);
                if (enquiryRepository.findByEnquiryReference(candidate).isEmpty()) {
                    entity.setEnquiryReference(candidate);
                    return enquiryRepository.save(entity);
                }
            }
            // Fallback timestamp-based suffix
            entity.setEnquiryReference(prefix + String.format("%05d", (System.currentTimeMillis() % 100000)));
            return enquiryRepository.save(entity);
        } finally {
            referenceLock.unlock();
        }
    }

    public Page<BusinessEnquiry> searchEnquiries(
            String search,
            String category,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size) {
        Specification<BusinessEnquiry> spec = buildSearchSpec(search, category, status, fromDate, toDate);
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return enquiryRepository.findAll(spec, pageable);
    }

    public Map<String, Object> getStats() {
        long total = enquiryRepository.count();
        long newCount = enquiryRepository.countByLeadStatus("New");
        long contacted = enquiryRepository.countByLeadStatus("Contacted");
        long meetings = enquiryRepository.countByLeadStatus("Meeting Scheduled");
        long proposalSent = enquiryRepository.countByLeadStatus("Proposal Sent");
        long negotiation = enquiryRepository.countByLeadStatus("Negotiation");
        long pending = newCount + contacted + meetings + proposalSent + negotiation;
        long converted = enquiryRepository.countByLeadStatus("Converted");
        long lost = enquiryRepository.countByLeadStatus("Lost");
        double conversionRate = total > 0 ? Math.round((converted * 10000.0) / total) / 100.0 : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalEnquiries", total);
        stats.put("newEnquiries", newCount);
        stats.put("pendingFollowUps", pending);
        stats.put("meetingsScheduled", meetings);
        stats.put("convertedLeads", converted);
        stats.put("lostLeads", lost);
        stats.put("conversionRate", conversionRate);
        return stats;
    }

    public Map<String, Object> getEnquiryDetail(Long id) {
        BusinessEnquiry enquiry = enquiryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Business enquiry not found with ID: " + id));

        List<ActivityLog> logs = activityLogRepository
                .findByEventTypeContainingIgnoreCaseOrderByCreatedAtDesc("ENQUIRY", PageRequest.of(0, 200))
                .getContent()
                .stream()
                .filter(item -> matchesEnquiryLog(item, enquiry))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enquiry", enquiry);
        result.put("auditLogs", logs);
        return result;
    }

    @Transactional
    public BusinessEnquiry updateEnquiry(Long id, BusinessEnquiryUpdateRequest req, String actorId) {
        BusinessEnquiry enquiry = enquiryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Business enquiry not found with ID: " + id));

        List<String> changes = new ArrayList<>();
        if (req.getLeadStatus() != null && !req.getLeadStatus().isBlank()) {
            String canonical = validateAndNormalizeStatus(req.getLeadStatus());
            if (!canonical.equals(enquiry.getLeadStatus())) {
                changes.add("status: " + enquiry.getLeadStatus() + " -> " + canonical);
                enquiry.setLeadStatus(canonical);
            }
        }
        if (req.getAssignedTo() != null) {
            String sanitizedAssignee = cleanText(req.getAssignedTo(), false);
            String target = sanitizedAssignee.isBlank() ? null : sanitizedAssignee;
            if (!Objects.equals(target, enquiry.getAssignedTo())) {
                changes.add("assignedTo: " + Objects.toString(enquiry.getAssignedTo(), "Unassigned")
                        + " -> " + Objects.toString(target, "Unassigned"));
                enquiry.setAssignedTo(target);
            }
        }
        if (req.getAdminNotes() != null) {
            String sanitizedNotes = cleanText(req.getAdminNotes(), true);
            String targetNotes = sanitizedNotes.isBlank() ? null : sanitizedNotes;
            if (!Objects.equals(targetNotes, enquiry.getAdminNotes())) {
                changes.add("adminNotes updated");
                enquiry.setAdminNotes(targetNotes);
            }
        }

        BusinessEnquiry saved = enquiryRepository.save(enquiry);
        if (!changes.isEmpty()) {
            recordAdminAudit(
                    actorId,
                    "BUSINESS_ENQUIRY_UPDATED",
                    "Updated enquiry " + saved.getEnquiryReference() + " (" + String.join(", ", changes) + ")",
                    saved
            );
        }
        return saved;
    }

    @Transactional
    public BusinessEnquiry updateStatus(Long id, String status, String actorId) {
        BusinessEnquiry enquiry = enquiryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Business enquiry not found with ID: " + id));
        String canonical = validateAndNormalizeStatus(status);
        String previous = enquiry.getLeadStatus();
        enquiry.setLeadStatus(canonical);
        BusinessEnquiry saved = enquiryRepository.save(enquiry);

        recordAdminAudit(
                actorId,
                "BUSINESS_ENQUIRY_STATUS_UPDATED",
                "Changed lead status for " + saved.getEnquiryReference() + " from " + previous + " to " + canonical,
                saved
        );
        return saved;
    }

    public BusinessEnquiry retryEmails(Long id, String actorId) {
        BusinessEnquiry enquiry = enquiryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Business enquiry not found with ID: " + id));

        BusinessEnquiry updated = emailService.dispatchEnquiryEmails(enquiry);
        recordAdminAudit(
                actorId,
                "BUSINESS_ENQUIRY_EMAIL_RETRY",
                "Retried email notifications for " + updated.getEnquiryReference() + " (status: " + updated.getEmailNotificationStatus() + ")",
                updated
        );
        return updated;
    }

    public List<Map<String, String>> listAssignees() {
        Map<String, Map<String, String>> unique = new LinkedHashMap<>();

        List<User> staffUsers = new ArrayList<>();
        for (String role : List.of("admin", "dual", "hr", "manager", "super_admin", "teacher")) {
            staffUsers.addAll(userRepository.findByRoleIgnoreCase(role));
        }

        for (User u : staffUsers) {
            if (u.getEmail() != null && !u.getEmail().isBlank()) {
                String key = u.getEmail().toLowerCase(Locale.ROOT);
                unique.putIfAbsent(key, Map.of(
                        "id", u.getId(),
                        "label", u.getEmail() + " (" + u.getRole().toUpperCase(Locale.ROOT) + ")",
                        "value", u.getEmail(),
                        "role", u.getRole()
                ));
            }
        }

        List<Member> members = memberRepository.findPublicDirectoryMembers();
        for (Member m : members) {
            String display = m.getFullName() + (m.getEmail() != null && !m.getEmail().isBlank() ? " <" + m.getEmail() + ">" : "");
            String key = (m.getEmail() != null && !m.getEmail().isBlank())
                    ? m.getEmail().toLowerCase(Locale.ROOT)
                    : m.getFullName().toLowerCase(Locale.ROOT);
            unique.putIfAbsent(key, Map.of(
                    "id", m.getId(),
                    "label", display + (m.getMemberType() != null ? " [" + m.getMemberType() + "]" : ""),
                    "value", m.getFullName(),
                    "role", m.getMemberType() != null ? m.getMemberType() : "Staff"
            ));
        }

        return new ArrayList<>(unique.values());
    }

    public byte[] exportEnquiries(
            String search,
            String category,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            String format,
            String actorId) {
        Specification<BusinessEnquiry> spec = buildSearchSpec(search, category, status, fromDate, toDate);
        List<BusinessEnquiry> rows = enquiryRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

        byte[] output;
        if ("xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format)) {
            output = buildExcelExport(rows);
        } else {
            output = buildCsvExport(rows);
        }

        try {
            ActivityLog logEntry = new ActivityLog();
            logEntry.setUserId(actorId != null ? actorId : "ADMIN");
            logEntry.setEventType("BUSINESS_ENQUIRY_EXPORT");
            logEntry.setDescription("Exported " + rows.size() + " business enquiries as " + (format != null ? format.toUpperCase(Locale.ROOT) : "CSV"));
            logEntry.setMetadata(Map.of(
                    "count", rows.size(),
                    "format", format != null ? format : "csv"
            ));
            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to record export audit log: {}", e.getMessage());
        }

        return output;
    }

    private Specification<BusinessEnquiry> buildSearchSpec(
            String search,
            String category,
            String status,
            LocalDate fromDate,
            LocalDate toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate byName = cb.like(cb.lower(root.get("fullName")), pattern);
                Predicate byEmail = cb.like(cb.lower(root.get("email")), pattern);
                Predicate byRef = cb.like(cb.lower(root.get("enquiryReference")), pattern);
                Predicate byOrg = cb.like(cb.lower(cb.coalesce(root.get("organizationName"), "")), pattern);
                predicates.add(cb.or(byName, byEmail, byRef, byOrg));
            }

            if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category.trim())) {
                predicates.add(cb.equal(cb.lower(root.get("enquiryType")), category.trim().toLowerCase(Locale.ROOT)));
            }

            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
                predicates.add(cb.equal(cb.lower(root.get("leadStatus")), status.trim().toLowerCase(Locale.ROOT)));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate.atStartOfDay()));
            }

            if (toDate != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), toDate.plusDays(1).atStartOfDay()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private byte[] buildCsvExport(List<BusinessEnquiry> rows) {
        StringBuilder sb = new StringBuilder();
        String[] headers = {
                "Reference ID", "Created At", "Full Name", "Organization / Institution", "Email",
                "Mobile Number", "City", "Designation", "Enquiry Category", "Selected Services",
                "Requirements", "Estimated Budget", "Expected Timeline", "Preferred Meeting",
                "Institution Type", "Department", "Student Count", "Collaboration Services",
                "Preferred Contact", "Marketing Source", "Lead Status", "Assigned To",
                "Email Status", "UTM Source", "UTM Medium", "UTM Campaign", "UTM Content", "Admin Notes"
        };
        sb.append(Arrays.stream(headers).map(this::csvCell).collect(Collectors.joining(","))).append("\r\n");

        for (BusinessEnquiry e : rows) {
            List<String> cols = List.of(
                    nonNull(e.getEnquiryReference()),
                    e.getCreatedAt() != null ? e.getCreatedAt().format(EXPORT_DATE_FMT) : "",
                    nonNull(e.getFullName()),
                    nonNull(e.getOrganizationName()),
                    nonNull(e.getEmail()),
                    nonNull(e.getMobileNumber()),
                    nonNull(e.getCity()),
                    nonNull(e.getDesignation()),
                    nonNull(e.getEnquiryType()),
                    joinList(e.getSelectedServices()),
                    nonNull(e.getRequirements()),
                    nonNull(e.getEstimatedBudget()),
                    nonNull(e.getExpectedTimeline()),
                    nonNull(e.getPreferredMeeting()),
                    nonNull(e.getInstitutionType()),
                    nonNull(e.getDepartment()),
                    nonNull(e.getStudentCount()),
                    joinList(e.getCollaborationServices()),
                    nonNull(e.getPreferredContactMethod()),
                    nonNull(e.getMarketingSource()),
                    nonNull(e.getLeadStatus()),
                    nonNull(e.getAssignedTo()),
                    nonNull(e.getEmailNotificationStatus()),
                    nonNull(e.getUtmSource()),
                    nonNull(e.getUtmMedium()),
                    nonNull(e.getUtmCampaign()),
                    nonNull(e.getUtmContent()),
                    nonNull(e.getAdminNotes())
            );
            sb.append(cols.stream().map(this::csvCell).collect(Collectors.joining(","))).append("\r\n");
        }

        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);
        return result;
    }

    private byte[] buildExcelExport(List<BusinessEnquiry> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Business Enquiries");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFont(headerFont);

            String[] headers = {
                    "Reference ID", "Created At", "Full Name", "Organization / Institution", "Email",
                    "Mobile Number", "City", "Designation", "Enquiry Category", "Selected Services",
                    "Requirements", "Estimated Budget", "Expected Timeline", "Preferred Meeting",
                    "Institution Type", "Department", "Student Count", "Collaboration Services",
                    "Preferred Contact", "Marketing Source", "Lead Status", "Assigned To",
                    "Email Status", "UTM Source", "UTM Medium", "UTM Campaign", "UTM Content", "Admin Notes"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (BusinessEnquiry e : rows) {
                Row row = sheet.createRow(r++);
                List<String> cols = List.of(
                        nonNull(e.getEnquiryReference()),
                        e.getCreatedAt() != null ? e.getCreatedAt().format(EXPORT_DATE_FMT) : "",
                        nonNull(e.getFullName()),
                        nonNull(e.getOrganizationName()),
                        nonNull(e.getEmail()),
                        nonNull(e.getMobileNumber()),
                        nonNull(e.getCity()),
                        nonNull(e.getDesignation()),
                        nonNull(e.getEnquiryType()),
                        joinList(e.getSelectedServices()),
                        nonNull(e.getRequirements()),
                        nonNull(e.getEstimatedBudget()),
                        nonNull(e.getExpectedTimeline()),
                        nonNull(e.getPreferredMeeting()),
                        nonNull(e.getInstitutionType()),
                        nonNull(e.getDepartment()),
                        nonNull(e.getStudentCount()),
                        joinList(e.getCollaborationServices()),
                        nonNull(e.getPreferredContactMethod()),
                        nonNull(e.getMarketingSource()),
                        nonNull(e.getLeadStatus()),
                        nonNull(e.getAssignedTo()),
                        nonNull(e.getEmailNotificationStatus()),
                        nonNull(e.getUtmSource()),
                        nonNull(e.getUtmMedium()),
                        nonNull(e.getUtmCampaign()),
                        nonNull(e.getUtmContent()),
                        nonNull(e.getAdminNotes())
                );
                for (int c = 0; c < cols.size(); c++) {
                    row.createCell(c).setCellValue(neutralizeSpreadsheetFormula(cols.get(c)));
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, i == 10 || i == 27 ? 12000 : 5200);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate Excel export", e);
        }
    }

    private String csvCell(String value) {
        String safe = neutralizeSpreadsheetFormula(value != null ? value : "");
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String neutralizeSpreadsheetFormula(String val) {
        if (val == null || val.isEmpty()) return "";
        char first = val.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + val;
        }
        return val;
    }

    private String validateAndNormalizeStatus(String status) {
        for (String allowed : ALLOWED_LEAD_STATUSES) {
            if (allowed.equalsIgnoreCase(status.trim())) {
                return allowed;
            }
        }
        throw new IllegalArgumentException("Invalid lead status '" + status + "'. Allowed values: " + ALLOWED_LEAD_STATUSES);
    }

    private void recordAdminAudit(String actorId, String eventType, String description, BusinessEnquiry enquiry) {
        try {
            ActivityLog entry = new ActivityLog();
            entry.setUserId(actorId != null && !actorId.isBlank() ? actorId : "ADMIN");
            entry.setEventType(eventType);
            entry.setDescription(description);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("enquiryId", enquiry.getId());
            metadata.put("enquiryReference", enquiry.getEnquiryReference());
            metadata.put("leadStatus", enquiry.getLeadStatus());
            if (enquiry.getAssignedTo() != null) {
                metadata.put("assignedTo", enquiry.getAssignedTo());
            }
            entry.setMetadata(metadata);
            activityLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to record admin audit log for {}: {}", enquiry.getEnquiryReference(), e.getMessage());
        }
    }

    private boolean matchesEnquiryLog(ActivityLog logEntry, BusinessEnquiry enquiry) {
        Map<String, Object> meta = logEntry.getMetadata();
        if (meta != null) {
            Object ref = meta.get("enquiryReference");
            if (ref != null && ref.toString().equals(enquiry.getEnquiryReference())) {
                return true;
            }
            Object idObj = meta.get("enquiryId");
            if (idObj != null && String.valueOf(enquiry.getId()).equals(String.valueOf(idObj))) {
                return true;
            }
        }
        return logEntry.getDescription() != null && logEntry.getDescription().contains(enquiry.getEnquiryReference());
    }

    private String resolveEnquiryType(String requestedCategory, List<String> selectedServices) {
        if (requestedCategory != null && !requestedCategory.isBlank()) {
            return cleanText(requestedCategory, false);
        }
        Set<String> categories = selectedServices.stream()
                .map(s -> SERVICE_TO_CATEGORY.getOrDefault(s.toLowerCase(Locale.ROOT), "OTHER"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (categories.size() == 1) {
            return categories.iterator().next();
        }
        return categories.iterator().next();
    }

    private String computeSubmissionHash(String email, List<String> services, String requirements) {
        try {
            String normalizedServices = services.stream()
                    .map(s -> s.toLowerCase(Locale.ROOT).trim())
                    .sorted()
                    .collect(Collectors.joining("|"));
            String normalizedReq = requirements.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
            String payload = email + "::" + normalizedServices + "::" + normalizedReq;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash submission", e);
        }
    }

    private String cleanNullable(String input, boolean multiline) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String cleaned = cleanText(input, multiline);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String cleanText(String input, boolean multiline) {
        if (input == null) return "";
        String noScript = SCRIPT_TAG_PATTERN.matcher(input).replaceAll("");
        String noHtml = HTML_TAG_PATTERN.matcher(noScript).replaceAll("");
        String noEvents = EVENT_HANDLER_PATTERN.matcher(noHtml).replaceAll("");
        String stripped = noEvents.replace("javascript:", "").replace("\u0000", "");
        if (!multiline) {
            return stripped.replaceAll("\\s+", " ").trim();
        }
        return stripped.replace("\r\n", "\n").trim();
    }

    private String nonNull(String s) {
        return s != null ? s : "";
    }

    private String joinList(List<String> list) {
        return list == null || list.isEmpty() ? "" : String.join("; ", list);
    }

    private static Map<String, String> buildServiceCategoryMap() {
        Map<String, String> map = new HashMap<>();
        for (String s : List.of(
                "Website Development", "Mobile App Development", "Custom Software Development",
                "E-commerce Development", "ERP / CRM / LMS Solutions")) {
            map.put(s.toLowerCase(Locale.ROOT), "IT & SOFTWARE");
        }
        for (String s : List.of(
                "Cybersecurity Services", "Vulnerability Assessment & Penetration Testing (VAPT)",
                "Security Audit", "SOC Services", "Network & Cloud Security")) {
            map.put(s.toLowerCase(Locale.ROOT), "CYBERSECURITY");
        }
        for (String s : List.of(
                "AI & Automation Solutions", "AI Agent Development", "Cloud Solutions", "DevOps Services")) {
            map.put(s.toLowerCase(Locale.ROOT), "AI & CLOUD");
        }
        for (String s : List.of(
                "Cybersecurity Training", "Corporate Training", "University / College Collaboration",
                "Internship / Apprenticeship Partnership", "Faculty Development Program",
                "Curriculum Development", "Cybersecurity Lab Setup", "Placement Assistance")) {
            map.put(s.toLowerCase(Locale.ROOT), "EDUCATION & TRAINING");
        }
        map.put("other business enquiry", "OTHER");
        return Collections.unmodifiableMap(map);
    }

    @Getter
    public static class DuplicateEnquiryException extends RuntimeException {
        private final String enquiryReference;

        public DuplicateEnquiryException(String message, String enquiryReference) {
            super(message);
            this.enquiryReference = enquiryReference;
        }
    }
}
