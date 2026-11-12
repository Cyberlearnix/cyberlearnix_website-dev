package com.cyberlearnix.user.service;

import com.cyberlearnix.shared.entity.user.ActivityLog;
import com.cyberlearnix.shared.entity.user.BusinessEnquiry;
import com.cyberlearnix.shared.repository.user.ActivityLogRepository;
import com.cyberlearnix.shared.repository.user.BusinessEnquiryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class BusinessEnquiryEmailService {

    public static final List<String> INTERNAL_RECIPIENTS = List.of(
            "connect@cyberlearnix.com",
            "cyberlearnix@gmail.com",
            "palakurlashivakumar.179@gmail.com"
    );

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'");

    private final BusinessEnquiryRepository enquiryRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ResendService resendService;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.public-url:https://www.cyberlearnix.com}")
    private String publicUrl;

    @Value("${spring.mail.username:cyberlearnixprivatelimited@gmail.com}")
    private String smtpFromAddress;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${enquiry.email.max-retries:3}")
    private int maxRetries = 3;

    @Value("${enquiry.email.retry-delay-ms:800}")
    private long retryDelayMs = 800L;

    @Autowired
    public BusinessEnquiryEmailService(
            BusinessEnquiryRepository enquiryRepository,
            ActivityLogRepository activityLogRepository,
            ResendService resendService,
            @Autowired(required = false) JavaMailSender mailSender) {
        this.enquiryRepository = enquiryRepository;
        this.activityLogRepository = activityLogRepository;
        this.resendService = resendService;
        this.mailSender = mailSender;
    }

    @Async
    public void sendEnquiryEmailsAsync(Long enquiryId) {
        enquiryRepository.findById(enquiryId).ifPresent(this::dispatchEnquiryEmails);
    }

    public BusinessEnquiry dispatchEnquiryEmails(BusinessEnquiry enquiry) {
        String internalSubject = "New Business Enquiry \u2013 Cyberlearnix [" + enquiry.getEnquiryReference() + "]";
        String internalHtml = buildInternalNotificationHtml(enquiry);

        String customerSubject = "Your Enquiry Has Been Received \u2013 Cyberlearnix Private Limited";
        String customerHtml = buildCustomerAcknowledgementHtml(enquiry);

        List<Map<String, Object>> deliveryResults = new ArrayList<>();
        int totalTargets = INTERNAL_RECIPIENTS.size() + 1;
        int successCount = 0;

        for (String recipient : INTERNAL_RECIPIENTS) {
            DeliveryResult res = sendWithRetry(recipient, internalSubject, internalHtml);
            deliveryResults.add(res.toMap("INTERNAL"));
            if (res.success) successCount++;
        }

        DeliveryResult custRes = sendWithRetry(enquiry.getEmail(), customerSubject, customerHtml);
        deliveryResults.add(custRes.toMap("CUSTOMER"));
        if (custRes.success) successCount++;

        String overallStatus;
        if (successCount == totalTargets) {
            overallStatus = "SENT";
        } else if (successCount > 0) {
            overallStatus = "PARTIAL";
        } else {
            overallStatus = "FAILED";
        }

        enquiry.setEmailNotificationStatus(overallStatus);
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("lastAttemptAt", LocalDateTime.now().toString());
            summary.put("status", overallStatus);
            summary.put("successCount", successCount);
            summary.put("totalTargets", totalTargets);
            summary.put("recipients", deliveryResults);
            enquiry.setEmailDeliveryDetails(objectMapper.writeValueAsString(summary));
        } catch (Exception e) {
            enquiry.setEmailDeliveryDetails("Status=" + overallStatus + ", success=" + successCount + "/" + totalTargets);
        }

        BusinessEnquiry saved = enquiryRepository.save(enquiry);

        if (!"SENT".equals(overallStatus)) {
            logEmailFailureForAdminReview(saved, deliveryResults, overallStatus);
        }

        return saved;
    }

    private DeliveryResult sendWithRetry(String to, String subject, String htmlContent) {
        int attempts = Math.max(1, maxRetries);
        String lastError = null;
        String channelUsed = "NONE";

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                if (resendService != null && resendService.sendEmail(to, subject, htmlContent)) {
                    return new DeliveryResult(to, true, "RESEND", attempt, null);
                }

                if (mailSender != null && smtpPassword != null && !smtpPassword.isBlank()) {
                    sendViaSmtp(to, subject, htmlContent);
                    return new DeliveryResult(to, true, "SMTP", attempt, null);
                } else {
                    lastError = "Email provider not configured (neither Resend API key nor SMTP password active)";
                    channelUsed = "UNCONFIGURED";
                }
            } catch (Exception ex) {
                lastError = ex.getMessage();
                channelUsed = "SMTP/RESEND";
                log.warn("Email delivery attempt {}/{} failed for {}: {}", attempt, attempts, to, lastError);
            }

            if (attempt < attempts && retryDelayMs > 0) {
                try {
                    Thread.sleep(retryDelayMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    lastError = "Retry interrupted";
                    break;
                }
            }
        }

        log.error("All {} email delivery attempts failed for recipient {}: {}", attempts, to, lastError);
        return new DeliveryResult(to, false, channelUsed, attempts, lastError);
    }

    private void sendViaSmtp(String to, String subject, String htmlContent) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        String fromAddr = (smtpFromAddress != null && !smtpFromAddress.isBlank())
                ? smtpFromAddress : "cyberlearnixprivatelimited@gmail.com";
        helper.setFrom("Cyberlearnix Private Limited <" + fromAddr + ">");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    private void logEmailFailureForAdminReview(BusinessEnquiry enquiry, List<Map<String, Object>> results, String status) {
        try {
            ActivityLog logEntry = new ActivityLog();
            logEntry.setUserId("SYSTEM");
            logEntry.setEventType("ENQUIRY_EMAIL_FAILURE");
            logEntry.setDescription("Email delivery " + status + " for enquiry " + enquiry.getEnquiryReference());
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (enquiry.getId() != null) metadata.put("enquiryId", enquiry.getId());
            if (enquiry.getEnquiryReference() != null) metadata.put("enquiryReference", enquiry.getEnquiryReference());
            metadata.put("emailStatus", status);
            metadata.put("recipients", results);
            logEntry.setMetadata(metadata);
            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to persist email failure audit log for {}: {}", enquiry.getEnquiryReference(), e.getMessage());
        }
    }

    public String buildInternalNotificationHtml(BusinessEnquiry e) {
        String base = (publicUrl != null && !publicUrl.isBlank()) ? publicUrl.replaceAll("/+$", "") : "https://www.cyberlearnix.com";
        String dashboardUrl = base + "/adminlms?view=enquiries&ref=" + esc(e.getEnquiryReference());

        String servicesText = (e.getSelectedServices() != null && !e.getSelectedServices().isEmpty())
                ? String.join(", ", e.getSelectedServices()) : "-";

        StringBuilder rows = new StringBuilder();
        appendRow(rows, "Enquiry Reference ID", "<strong>" + esc(e.getEnquiryReference()) + "</strong>");
        appendRow(rows, "Customer Name", esc(e.getFullName()));
        appendRow(rows, "Company / Institution", escOrDash(e.getOrganizationName()));
        appendRow(rows, "Email Address", "<a href='mailto:" + esc(e.getEmail()) + "' style='color:#0057FF;text-decoration:none;'>" + esc(e.getEmail()) + "</a>");
        appendRow(rows, "Phone Number", esc(e.getMobileNumber()));
        appendRow(rows, "City / Location", esc(e.getCity()));
        appendRow(rows, "Designation", escOrDash(e.getDesignation()));
        appendRow(rows, "Enquiry Category", esc(e.getEnquiryType()));
        appendRow(rows, "Selected Services", esc(servicesText));
        appendRow(rows, "Estimated Budget", escOrDash(e.getEstimatedBudget()));
        appendRow(rows, "Expected Timeline", escOrDash(e.getExpectedTimeline()));
        appendRow(rows, "Preferred Meeting", escOrDash(e.getPreferredMeeting()));
        appendRow(rows, "Preferred Contact", escOrDash(e.getPreferredContactMethod()));
        appendRow(rows, "Marketing Source", escOrDash(e.getMarketingSource()));
        if (hasText(e.getUtmSource()) || hasText(e.getUtmCampaign())) {
            String utmSummary = String.format("source=%s | medium=%s | campaign=%s | content=%s",
                    escOrDash(e.getUtmSource()), escOrDash(e.getUtmMedium()),
                    escOrDash(e.getUtmCampaign()), escOrDash(e.getUtmContent()));
            appendRow(rows, "Campaign (UTM)", utmSummary);
        }
        appendRow(rows, "Submission Timestamp", formatIst(e.getCreatedAt()));

        StringBuilder universityBlock = new StringBuilder();
        if (hasText(e.getInstitutionType()) || hasText(e.getDepartment()) || hasText(e.getStudentCount())
                || (e.getCollaborationServices() != null && !e.getCollaborationServices().isEmpty())) {
            universityBlock.append("<div style='margin:22px 0;padding:18px;border-radius:10px;background:#f0f9ff;border:1px solid #bae6fd;'>")
                    .append("<h3 style='margin:0 0 12px 0;font-size:15px;color:#0F172A;'>University &amp; College Collaboration Details</h3>")
                    .append("<table style='width:100%;border-collapse:collapse;font-size:13px;'>");
            appendRow(universityBlock, "Institution Type", escOrDash(e.getInstitutionType()));
            appendRow(universityBlock, "Department / Branch", escOrDash(e.getDepartment()));
            appendRow(universityBlock, "Approx. Students", escOrDash(e.getStudentCount()));
            String collab = (e.getCollaborationServices() != null && !e.getCollaborationServices().isEmpty())
                    ? String.join(", ", e.getCollaborationServices()) : "-";
            appendRow(universityBlock, "Required Collaboration", esc(collab));
            universityBlock.append("</table></div>");
        }

        String content = ""
                + "<div style='display:inline-block;padding:5px 12px;border-radius:999px;background:#fff7ed;color:#c2410c;border:1px solid #fed7aa;font-size:12px;font-weight:700;margin-bottom:14px;'>"
                + "NEW BUSINESS &amp; PARTNERSHIP LEAD"
                + "</div>"
                + "<h2 style='margin:0 0 8px 0;color:#0F172A;font-size:20px;'>New Business Enquiry Received</h2>"
                + "<p style='margin:0 0 20px 0;color:#475569;font-size:14px;'>A prospective client or partner submitted an enquiry on the Cyberlearnix website. Details are below:</p>"
                + "<table style='width:100%;border-collapse:collapse;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;font-size:14px;'>"
                + rows
                + "</table>"
                + "<div style='margin:22px 0;'>"
                + "<p style='margin:0 0 8px 0;font-size:13px;font-weight:700;color:#0F172A;text-transform:uppercase;letter-spacing:0.5px;'>Project Description / Requirements</p>"
                + "<div style='background:#f8fafc;padding:16px;border-radius:10px;border:1px solid #e2e8f0;color:#1e293b;font-size:14px;line-height:1.65;white-space:pre-wrap;'>"
                + esc(e.getRequirements())
                + "</div></div>"
                + universityBlock
                + "<div style='text-align:center;margin:28px 0 10px 0;'>"
                + "<a href='" + dashboardUrl + "' style='display:inline-block;background:#0057FF;color:#ffffff;padding:14px 28px;border-radius:8px;text-decoration:none;font-weight:700;font-size:14px;letter-spacing:0.4px;'>VIEW ENQUIRY IN ADMIN DASHBOARD</a>"
                + "</div>";

        return wrapInCorporateTemplate("Business Enquiry Notification", content);
    }

    public String buildCustomerAcknowledgementHtml(BusinessEnquiry e) {
        String servicesText = (e.getSelectedServices() != null && !e.getSelectedServices().isEmpty())
                ? String.join(", ", e.getSelectedServices()) : esc(e.getEnquiryType());

        String content = ""
                + "<h2 style='margin:0 0 12px 0;color:#0F172A;font-size:21px;'>Dear " + esc(e.getFullName()) + ",</h2>"
                + "<p style='margin:0 0 14px 0;color:#334155;font-size:15px;line-height:1.7;'>Thank you for contacting <strong>Cyberlearnix Private Limited</strong>.</p>"
                + "<p style='margin:0 0 22px 0;color:#334155;font-size:15px;line-height:1.7;'>We have successfully received your enquiry. Our team will review your requirements and contact you shortly.</p>"
                + "<div style='background:#f8fafc;border:1px solid #e2e8f0;border-left:4px solid #0057FF;border-radius:10px;padding:18px 20px;margin:20px 0;'>"
                + "<p style='margin:0 0 8px 0;font-size:13px;color:#64748b;text-transform:uppercase;letter-spacing:0.6px;font-weight:700;'>Enquiry Summary</p>"
                + "<p style='margin:0 0 6px 0;font-size:14px;color:#0F172A;'><strong>Enquiry Reference Number:</strong> <span style='color:#0057FF;font-family:monospace;font-weight:700;'>" + esc(e.getEnquiryReference()) + "</span></p>"
                + "<p style='margin:0 0 6px 0;font-size:14px;color:#0F172A;'><strong>Submitted Date:</strong> " + formatIst(e.getCreatedAt()) + "</p>"
                + "<p style='margin:0;font-size:14px;color:#0F172A;'><strong>Selected Interest:</strong> " + esc(servicesText) + "</p>"
                + "</div>"
                + "<div style='background:#fff7ed;border:1px solid #fed7aa;border-radius:10px;padding:16px 20px;margin:22px 0;'>"
                + "<p style='margin:0 0 8px 0;font-size:14px;font-weight:700;color:#9a3412;'>For immediate assistance:</p>"
                + "<p style='margin:0 0 4px 0;font-size:14px;color:#1e293b;'><strong>Phone:</strong> +91 6305203756</p>"
                + "<p style='margin:0;font-size:14px;color:#1e293b;'><strong>Email:</strong> <a href='mailto:connect@cyberlearnix.com' style='color:#0057FF;text-decoration:none;'>connect@cyberlearnix.com</a></p>"
                + "</div>"
                + "<p style='margin:24px 0 2px 0;font-size:15px;font-weight:700;color:#0F172A;'>Cyberlearnix Private Limited</p>"
                + "<p style='margin:0;font-size:13px;font-weight:600;color:#0057FF;letter-spacing:0.4px;'>Learn. Code. Innovate. Grow.</p>";

        return wrapInCorporateTemplate("Enquiry Confirmation", content);
    }

    private String wrapInCorporateTemplate(String heading, String content) {
        return "<!DOCTYPE html><html><body style='margin:0;padding:24px;background:#f1f5f9;'>"
                + "<div style=\"font-family:'Inter','Segoe UI',Arial,sans-serif;max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:14px;overflow:hidden;box-shadow:0 8px 24px rgba(15,23,42,0.06);\">"
                + "  <div style=\"background:#0F172A;padding:28px 32px;border-bottom:4px solid #F97316;\">"
                + "    <p style=\"margin:0 0 4px 0;color:#38bdf8;font-size:11px;font-weight:700;letter-spacing:1.8px;text-transform:uppercase;\">CYBERLEARNIX PRIVATE LIMITED</p>"
                + "    <h1 style=\"margin:0;color:#ffffff;font-size:22px;font-weight:700;letter-spacing:0.2px;\">" + esc(heading) + "</h1>"
                + "  </div>"
                + "  <div style=\"padding:32px;background:#ffffff;color:#1e293b;\">"
                + content
                + "  </div>"
                + "  <div style=\"background:#f8fafc;padding:20px 32px;border-top:1px solid #e2e8f0;text-align:center;color:#64748b;\">"
                + "    <p style=\"margin:0;font-size:12px;font-weight:600;color:#334155;\">Cyberlearnix Private Limited &bull; Learn. Code. Innovate. Grow.</p>"
                + "    <p style=\"margin:6px 0 0 0;font-size:12px;\">Hyderabad, Telangana, India | +91 6305203756 | connect@cyberlearnix.com</p>"
                + "  </div>"
                + "</div>"
                + "</body></html>";
    }

    private void appendRow(StringBuilder sb, String label, String valueHtml) {
        sb.append("<tr>")
                .append("<td style='padding:10px 14px;border-bottom:1px solid #e2e8f0;background:#f8fafc;color:#475569;font-weight:600;width:36%;vertical-align:top;'>")
                .append(esc(label))
                .append("</td>")
                .append("<td style='padding:10px 14px;border-bottom:1px solid #e2e8f0;color:#0f172a;vertical-align:top;'>")
                .append(valueHtml)
                .append("</td>")
                .append("</tr>");
    }

    private String formatIst(LocalDateTime dt) {
        LocalDateTime target = dt != null ? dt : LocalDateTime.now();
        return target.atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                .format(DISPLAY_FMT);
    }

    private String esc(String val) {
        return val == null ? "" : HtmlUtils.htmlEscape(val);
    }

    private String escOrDash(String val) {
        return hasText(val) ? HtmlUtils.htmlEscape(val) : "&mdash;";
    }

    private boolean hasText(String val) {
        return val != null && !val.isBlank();
    }

    private record DeliveryResult(String recipient, boolean success, String channel, int attempts, String error) {
        Map<String, Object> toMap(String role) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("role", role);
            map.put("recipient", recipient);
            map.put("success", success);
            map.put("channel", channel);
            map.put("attempts", attempts);
            if (error != null) map.put("error", error);
            return map;
        }
    }
}
