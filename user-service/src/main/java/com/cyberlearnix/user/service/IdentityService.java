package com.cyberlearnix.user.service;

import com.cyberlearnix.shared.entity.identity.*;
import com.cyberlearnix.shared.repository.identity.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class IdentityService {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private IdentityEnrollmentFormRepository formRepository;
    @Autowired
    private IdentityEnrollmentResponseRepository responseRepository;
    @Autowired
    private IdentityAuditLogRepository auditLogRepository;
    @Autowired
    private EmailNotificationService emailNotificationService;

    private String publicUrl;

    @Value("${app.public-url:https://www.cyberlearnix.com}")
    public void setPublicUrl(String url) {
        if (url != null) {
            url = url.trim();
            url = url.replace("verify.cyberlearnix.com", "www.cyberlearnix.com");
            url = url.replace("verify.cyberlearnix", "www.cyberlearnix.com");
            url = url.replace("https://cyberlearnix.com", "https://www.cyberlearnix.com");
        }
        this.publicUrl = url;
    }

    private static final int QR_SIZE = 400;

    // --- Enrollment Form Module ---

    public IdentityEnrollmentForm createForm(IdentityEnrollmentForm form) {
        form.setCreatedAt(LocalDateTime.now());
        form.setUpdatedAt(LocalDateTime.now());
        return formRepository.save(form);
    }

    public IdentityEnrollmentForm updateForm(String id, IdentityEnrollmentForm details) {
        IdentityEnrollmentForm form = formRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Form not found"));
        form.setTitle(details.getTitle());
        form.setDescription(details.getDescription());
        form.setRoleType(details.getRoleType());
        form.setAssociatedCourse(details.getAssociatedCourse());
        form.setCustomFields(details.getCustomFields());
        form.setStartDate(details.getStartDate());
        form.setEndDate(details.getEndDate());
        form.setPaymentRequired(details.getPaymentRequired());
        form.setMaxResponses(details.getMaxResponses());
        form.setStatus(details.getStatus());
        form.setUpdatedAt(LocalDateTime.now());
        return formRepository.save(form);
    }

    public void deleteForm(String id) {
        formRepository.deleteById(id);
    }

    public List<IdentityEnrollmentForm> listActiveForms() {
        return formRepository.findByStatus("Active");
    }

    public List<IdentityEnrollmentForm> listAllForms() {
        return formRepository.findAll();
    }

    public IdentityEnrollmentForm getForm(String id) {
        return formRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Form not found"));
    }

    // --- Form Submissions (Responses) ---

    public IdentityEnrollmentResponse submitResponse(String formId, IdentityEnrollmentResponse response) {
        IdentityEnrollmentForm form = formRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Form not found"));

        if (!"Active".equalsIgnoreCase(form.getStatus())) {
            throw new RuntimeException("This enrollment form is currently inactive.");
        }
        if (form.getEndDate() != null && form.getEndDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("This enrollment form has expired.");
        }

        response.setFormId(formId);
        response.setStatus("Pending");
        response.setSubmittedAt(LocalDateTime.now());
        IdentityEnrollmentResponse saved = responseRepository.save(response);

        // Audit Log
        logAudit(null, "SUBMITTED", "System",
                "User " + response.getFullName() + " submitted application for " + form.getRoleType());

        return saved;
    }

    public List<IdentityEnrollmentResponse> listResponses(String status) {
        if (status == null || status.isBlank()) {
            return responseRepository.findAll();
        }
        return responseRepository.findByStatus(status);
    }

    public IdentityEnrollmentResponse getResponse(String id) {
        return responseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Response not found"));
    }

    // --- Approval Workflow ---

    @Transactional
    public Member reviewResponse(String id, String status, String remarks, String adminEmail) {
        IdentityEnrollmentResponse response = responseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Response not found"));

        response.setStatus(status);
        response.setRemarks(remarks);
        response.setReviewedBy(adminEmail);
        response.setReviewedAt(LocalDateTime.now());
        responseRepository.save(response);

        logAudit(null, status.toUpperCase(), adminEmail,
                "Application ID " + id + " for " + response.getFullName() + " marked as " + status + ". Remarks: "
                        + remarks);

        if ("Approved".equalsIgnoreCase(status)) {
            IdentityEnrollmentForm form = formRepository.findById(response.getFormId())
                    .orElseThrow(() -> new RuntimeException("Associated Form not found"));

            return approveAndCreateMember(response, form.getRoleType(), adminEmail);
        }

        // Notify user about rejection or changes requested
        if ("Rejected".equalsIgnoreCase(status)) {
            sendRejectionEmail(response.getEmail(), response.getFullName(), remarks);
        } else if ("ChangesRequested".equalsIgnoreCase(status)) {
            sendChangesRequestedEmail(response.getEmail(), response.getFullName(), remarks);
        }

        return null;
    }

    private Member approveAndCreateMember(IdentityEnrollmentResponse response, String roleType, String adminEmail) {
        Member member = new Member();
        member.setFullName(response.getFullName());
        member.setEmail(response.getEmail());
        member.setPhone(response.getPhone());
        member.setMemberType(normalizeRoleType(roleType));
        member.setProfilePhoto(response.getPhotoUrl());
        member.setStatus("Approved");
        member.setJoiningDate(LocalDate.now());
        member.setIsActive(true);
        member.setApprovedBy(adminEmail);
        member.setApprovedDate(LocalDateTime.now());

        // Auto-generate Member ID
        String memberId = generateMemberId(roleType);
        member.setMemberId(memberId);

        // Generate QR pointing to verification URL
        String verifyUrl = publicUrl + (publicUrl.endsWith("/") ? "" : "/") + "verify.html?enrollment=" + memberId;
        member.setVerificationUrl(verifyUrl);

        try {
            String qrBase64 = generateQrCode(verifyUrl);
            member.setQrCodeUrl(qrBase64);
        } catch (Exception e) {
            member.setQrCodeUrl("");
            System.err.println("QR Code Generation failed for " + memberId + ": " + e.getMessage());
        }

        Member savedMember = memberRepository.save(member);

        logAudit(savedMember.getMemberId(), "APPROVED", adminEmail,
                "Approved and created identity " + memberId + " for " + member.getFullName());

        // Send Email & WhatsApp Notifications
        sendApprovalEmail(savedMember);
        triggerMockWhatsAppNotification(savedMember);

        return savedMember;
    }

    // --- Employee & Member Management Module ---

    public Member addMemberManually(Member member, String adminEmail) {
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        member.setStatus("Approved");
        member.setIsActive(true);
        // Normalise memberType to consistent Title Case
        member.setMemberType(normalizeRoleType(member.getMemberType()));

        if (member.getMemberId() == null || member.getMemberId().isBlank()) {
            member.setMemberId(generateMemberId(member.getMemberType()));
        }

        String verifyUrl = publicUrl + (publicUrl.endsWith("/") ? "" : "/") + "verify.html?enrollment=" + member.getMemberId();
        member.setVerificationUrl(verifyUrl);

        try {
            member.setQrCodeUrl(generateQrCode(verifyUrl));
        } catch (Exception e) {
            member.setQrCodeUrl("");
        }

        Member saved = memberRepository.save(member);
        logAudit(saved.getMemberId(), "CREATED_MANUALLY", adminEmail,
                "Manually created member identity " + saved.getMemberId() + " (" + saved.getFullName() + ")");

        return saved;
    }

    public Member updateMember(String id, Member details, String adminEmail) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        member.setFullName(details.getFullName());
        member.setEmail(details.getEmail());
        member.setPhone(details.getPhone());
        member.setProfilePhoto(details.getProfilePhoto());
        member.setUpdatedAt(LocalDateTime.now());

        Member saved = memberRepository.save(member);
        logAudit(saved.getMemberId(), "UPDATED", adminEmail,
                "Updated personal details of member " + saved.getMemberId());

        return saved;
    }

    @Transactional
    public Member promoteMember(String id, String newDesignation, String newRoleType, String adminEmail) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        String oldRoleType = member.getMemberType();
        String oldMemberId = member.getMemberId();

        member.setDesignation(newDesignation);

        // If Role Type changes, we generate a NEW Member ID and new QR Code!
        if (newRoleType != null && !newRoleType.equalsIgnoreCase(oldRoleType)) {
            String normalizedRoleType = normalizeRoleType(newRoleType);
            member.setMemberType(normalizedRoleType);
            String newMemberId = generateMemberId(normalizedRoleType);
            member.setMemberId(newMemberId);

            String verifyUrl = publicUrl + (publicUrl.endsWith("/") ? "" : "/") + "verify.html?enrollment=" + newMemberId;
            member.setVerificationUrl(verifyUrl);

            try {
                member.setQrCodeUrl(generateQrCode(verifyUrl));
            } catch (Exception e) {
                member.setQrCodeUrl("");
            }

            logAudit(newMemberId, "PROMOTED", adminEmail,
                    "Promoted member from " + oldRoleType + " (ID: " + oldMemberId + ") to " + newRoleType + " (ID: "
                            + newMemberId + "). Designation: " + newDesignation);
        } else {
            logAudit(oldMemberId, "PROMOTED", adminEmail,
                    "Updated designation of member " + oldMemberId + " to " + newDesignation);
        }

        member.setUpdatedAt(LocalDateTime.now());
        return memberRepository.save(member);
    }

    public Member transferDepartment(String id, String newDepartment, String adminEmail) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        String oldDept = member.getDepartment();
        member.setDepartment(newDepartment);
        member.setUpdatedAt(LocalDateTime.now());

        Member saved = memberRepository.save(member);
        logAudit(saved.getMemberId(), "DEPT_TRANSFER", adminEmail,
                "Transferred member " + saved.getMemberId() + " from " + oldDept + " to " + newDepartment);

        return saved;
    }

    public Member deactivateMember(String id, String adminEmail) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        member.setIsActive(false);
        member.setStatus("Deactivated");
        member.setUpdatedAt(LocalDateTime.now());

        Member saved = memberRepository.save(member);
        logAudit(saved.getMemberId(), "DEACTIVATED", adminEmail,
                "Deactivated member identity " + saved.getMemberId());

        return saved;
    }

    public Member regenerateQr(String id, String adminEmail) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        String verifyUrl = publicUrl + (publicUrl.endsWith("/") ? "" : "/") + "verify.html?enrollment=" + member.getMemberId();
        member.setVerificationUrl(verifyUrl);

        try {
            member.setQrCodeUrl(generateQrCode(verifyUrl));
        } catch (Exception e) {
            throw new RuntimeException("Could not generate QR code");
        }

        member.setUpdatedAt(LocalDateTime.now());
        Member saved = memberRepository.save(member);

        logAudit(saved.getMemberId(), "QR_REGENERATED", adminEmail,
                "Regenerated QR code for member " + saved.getMemberId());

        return saved;
    }

    public Page<Member> searchMembers(String query, String type, String department, String status, Boolean isActive,
            Pageable pageable) {
        return memberRepository.searchMembers(
                query == null || query.isBlank() ? null : query.trim().toLowerCase(),
                type == null || type.isBlank() ? null : type.trim().toLowerCase(),
                department == null || department.isBlank() ? null : department.trim().toLowerCase(),
                status == null || status.isBlank() ? null : status.trim().toLowerCase(),
                pageable);
    }

    public List<String> listDepartments() {
        return memberRepository.findDistinctDepartments();
    }

    public Member getMember(String id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Member not found"));
    }

    public Member getMemberByCustomId(String customId) {
        return memberRepository.findByMemberId(customId)
                .orElseThrow(() -> new RuntimeException("Member ID not found"));
    }

    // --- ID Generator Helper ---

    private synchronized String generateMemberId(String roleType) {
        long currentCount = memberRepository.countByMemberType(roleType);
        String prefix = getRolePrefix(roleType);

        // CEO/Founder do not include year
        if ("CEO".equalsIgnoreCase(roleType) || "Founder".equalsIgnoreCase(roleType)) {
            return String.format("CLX-%s-%04d", prefix, currentCount + 1);
        }

        int currentYear = LocalDate.now().getYear();
        return String.format("CLX-%s-%d-%04d", prefix, currentYear, currentCount + 1);
    }

    /** Normalise role type to Title Case so DB values are consistent regardless of input casing. */
    private String normalizeRoleType(String roleType) {
        if (roleType == null || roleType.isBlank()) return roleType;
        String lower = roleType.trim().toLowerCase();
        switch (lower) {
            case "student":       return "Student";
            case "intern":        return "Intern";
            case "instructor":    return "Instructor";
            case "mentor":        return "Mentor";
            case "employee":      return "Employee";
            case "hr":            return "HR";
            case "team lead":     return "Team Lead";
            case "manager":       return "Manager";
            case "director":      return "Director";
            case "ceo":           return "CEO";
            case "founder":       return "Founder";
            case "consultant":    return "Consultant";
            case "guest speaker": return "Guest Speaker";
            case "partner":       return "Partner";
            default:
                // Generic Title Case for unknown values
                String[] words = lower.split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    if (!w.isEmpty()) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                    }
                }
                return sb.toString();
        }
    }

    private String getRolePrefix(String roleType) {
        if (roleType == null)
            return "MEM";
        switch (roleType.toLowerCase().trim()) {
            case "student":
                return "STU";
            case "intern":
                return "INT";
            case "instructor":
                return "INS";
            case "mentor":
                return "MENT";
            case "employee":
                return "EMP";
            case "hr":
                return "HR";
            case "team lead":
                return "TL";
            case "manager":
                return "MGR";
            case "director":
                return "DIR";
            case "ceo":
                return "CEO";
            case "founder":
                return "FOUND";
            case "consultant":
                return "CONS";
            case "guest speaker":
                return "GS";
            case "partner":
                return "PART";
            default:
                return "MEM";
        }
    }

    // --- QR Generator Helper ---

    private String generateQrCode(String content) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

        BufferedImage qrImage = new BufferedImage(QR_SIZE, QR_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < QR_SIZE; x++) {
            for (int y = 0; y < QR_SIZE; y++) {
                qrImage.setRGB(x, y, bitMatrix.get(x, y) ? 0xFF1a2756 : 0xFFFFFFFF);
            }
        }

        // Try overlay logo
        try {
            org.springframework.core.io.ClassPathResource logoResource = new org.springframework.core.io.ClassPathResource(
                    "logo.png");
            if (logoResource.exists()) {
                BufferedImage logo = ImageIO.read(logoResource.getInputStream());
                int logoSize = QR_SIZE / 5;
                int logoX = (QR_SIZE - logoSize) / 2;
                int logoY = (QR_SIZE - logoSize) / 2;

                BufferedImage overlay = new BufferedImage(QR_SIZE, QR_SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = overlay.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int padding = 8;
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Float(
                        logoX - padding, logoY - padding,
                        logoSize + padding * 2, logoSize + padding * 2,
                        16, 16));
                g2.drawImage(logo, logoX, logoY, logoSize, logoSize, null);
                g2.dispose();

                Graphics2D qrG = qrImage.createGraphics();
                qrG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                qrG.drawImage(overlay, 0, 0, null);
                qrG.dispose();
            }
        } catch (Exception logoEx) {
            System.err.println("Logo overlay failed, generating clean QR: " + logoEx.getMessage());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "PNG", baos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    // --- Audit & Notification Helpers ---

    private void logAudit(String memberId, String action, String performedBy, String details) {
        IdentityAuditLog log = new IdentityAuditLog();
        log.setMemberId(memberId);
        log.setAction(action);
        log.setPerformedBy(performedBy);
        log.setDetails(details);
        log.setTimestamp(LocalDateTime.now());
        auditLogRepository.save(log);
    }

    private void sendApprovalEmail(Member member) {
        String subject = "Your CyberLearnix Digital Credentials - Approved!";
        String content = "<h3>Dear " + member.getFullName() + ",</h3>" +
                "<p>We are pleased to inform you that your registration/enrollment with <strong>CyberLearnix Private Limited</strong> has been approved!</p>"
                +
                "<p>Your official Digital Identity Details are as follows:</p>" +
                "<table style='width: 100%; border-collapse: collapse; margin-bottom: 20px;'>" +
                "<tr><td style='padding: 8px 0; font-weight: bold; width: 150px;'>Member ID:</td><td style='padding: 8px 0; color: #1a2756; font-weight: bold;'>"
                + member.getMemberId() + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Type:</td><td style='padding: 8px 0;'>"
                + member.getMemberType() + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Department:</td><td style='padding: 8px 0;'>"
                + (member.getDepartment() != null ? member.getDepartment() : "General") + "</td></tr>" +
                "<tr><td style='padding: 8px 0; font-weight: bold;'>Joining Date:</td><td style='padding: 8px 0;'>"
                + member.getJoiningDate() + "</td></tr>" +
                "</table>" +
                "<p>You can scan your secure ID card QR Code to access your public verification profile: <a href='"
                + member.getVerificationUrl() + "' target='_blank'>" + member.getVerificationUrl() + "</a></p>" +
                "<p>Welcome to CyberLearnix!</p>" +
                "<p>Best regards,<br/>CyberLearnix HR Team</p>";
        emailNotificationService.sendEmail(member.getEmail(), subject, content);
    }

    private void sendRejectionEmail(String to, String name, String remarks) {
        String subject = "CyberLearnix Application Update";
        String content = "<h3>Dear " + name + ",</h3>" +
                "<p>Thank you for submitting your application to CyberLearnix.</p>" +
                "<p>After careful review, we regret to inform you that your application could not be approved at this time.</p>"
                +
                "<p><strong>Remarks from Reviewer:</strong></p>" +
                "<div style='background: #fff5f5; padding: 15px; border-radius: 6px; border: 1px solid #fed7d7; color: #c53030;'>"
                +
                remarks + "</div>" +
                "<p>If you have any questions, feel free to contact us.</p>" +
                "<p>Best regards,<br/>CyberLearnix Team</p>";
        emailNotificationService.sendEmail(to, subject, content);
    }

    private void sendChangesRequestedEmail(String to, String name, String remarks) {
        String subject = "Action Required: CyberLearnix Application Changes Requested";
        String content = "<h3>Dear " + name + ",</h3>" +
                "<p>The reviewer has requested changes/clarification on your application submission.</p>" +
                "<p><strong>Action Required:</strong></p>" +
                "<div style='background: #fffaf0; padding: 15px; border-radius: 6px; border: 1px solid #feebc8; color: #dd6b20;'>"
                +
                remarks + "</div>" +
                "<p>Please log back in or get in touch with HR to provide the requested details so that we can proceed with your approval.</p>"
                +
                "<p>Best regards,<br/>CyberLearnix Team</p>";
        emailNotificationService.sendEmail(to, subject, content);
    }

    private void triggerMockWhatsAppNotification(Member member) {
        // Mock WhatsApp integration - prints logging output for verification
        System.out.println("[WhatsApp Mock Notification] Sending WhatsApp message to: " + member.getPhone());
        System.out.println("[WhatsApp Mock Notification] Message: Hello " + member.getFullName() +
                ", your CyberLearnix ID card has been issued! ID: " + member.getMemberId() +
                ". Verify here: " + member.getVerificationUrl());
    }

    public List<IdentityAuditLog> getLogsForMember(String memberId) {
        return auditLogRepository.findByMemberIdOrderByTimestampDesc(memberId);
    }

    public List<IdentityAuditLog> getRecentLogs() {
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }

    // --- Stats / Dashboard Metrics ---

    public Map<String, Object> getIdentityStats() {
        long totalMembers = memberRepository.count();
        long students = memberRepository.countByMemberType("Student");
        long interns = memberRepository.countByMemberType("Intern");
        long employees = memberRepository.countEmployees();
        long instructors = memberRepository.countByMemberType("Instructor");
        long managers = memberRepository.countByMemberType("Manager");

        long pending = responseRepository.countByStatus("Pending");
        long rejected = responseRepository.countByStatus("Rejected");

        List<Member> recentlyAdded = memberRepository.findTop5ByOrderByCreatedAtDesc();

        return Map.of(
                "totalMembers", totalMembers,
                "students", students,
                "interns", interns,
                "employees", employees,
                "instructors", instructors,
                "managers", managers,
                "pendingApprovals", pending,
                "rejectedRequests", rejected,
                "recentlyAdded", recentlyAdded);
    }
}
