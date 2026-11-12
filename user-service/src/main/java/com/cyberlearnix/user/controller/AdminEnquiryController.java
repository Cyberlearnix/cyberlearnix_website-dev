package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.BusinessEnquiry;
import com.cyberlearnix.user.dto.BusinessEnquiryStatusRequest;
import com.cyberlearnix.user.dto.BusinessEnquiryUpdateRequest;
import com.cyberlearnix.user.service.BusinessEnquiryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/enquiries")
@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'SUPER_ADMIN', 'DUAL')")
public class AdminEnquiryController {

    private final BusinessEnquiryService enquiryService;

    public AdminEnquiryController(BusinessEnquiryService enquiryService) {
        this.enquiryService = enquiryService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listEnquiries(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<BusinessEnquiry> resultPage = enquiryService.searchEnquiries(
                search, category, status, fromDate, toDate, page, size);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", resultPage.getContent());
        body.put("totalElements", resultPage.getTotalElements());
        body.put("totalPages", resultPage.getTotalPages());
        body.put("page", resultPage.getNumber());
        body.put("size", resultPage.getSize());
        body.put("stats", enquiryService.getStats());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(enquiryService.getStats());
    }

    @GetMapping("/assignees")
    public ResponseEntity<List<Map<String, String>>> listAssignees() {
        return ResponseEntity.ok(enquiryService.listAssignees());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportEnquiries(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "csv") String format,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {

        String actor = resolveActor(headerUserId, authentication);
        boolean isExcel = "xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format);
        byte[] fileBytes = enquiryService.exportEnquiries(search, category, status, fromDate, toDate, format, actor);

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        String filename = "cyberlearnix-business-enquiries-" + stamp + (isExcel ? ".xlsx" : ".csv");
        MediaType mediaType = isExcel
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv; charset=UTF-8");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentType(mediaType);
        headers.setContentLength(fileBytes.length);

        return ResponseEntity.ok().headers(headers).body(fileBytes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEnquiryDetail(@PathVariable Long id) {
        return ResponseEntity.ok(enquiryService.getEnquiryDetail(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BusinessEnquiry> updateEnquiry(
            @PathVariable Long id,
            @Valid @RequestBody BusinessEnquiryUpdateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {
        String actor = resolveActor(headerUserId, authentication);
        return ResponseEntity.ok(enquiryService.updateEnquiry(id, request, actor));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BusinessEnquiry> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody BusinessEnquiryStatusRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {
        String actor = resolveActor(headerUserId, authentication);
        return ResponseEntity.ok(enquiryService.updateStatus(id, request.getLeadStatus(), actor));
    }

    @PostMapping("/{id}/retry-email")
    public ResponseEntity<BusinessEnquiry> retryEmail(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            Authentication authentication) {
        String actor = resolveActor(headerUserId, authentication);
        return ResponseEntity.ok(enquiryService.retryEmails(id, actor));
    }

    private String resolveActor(String headerUserId, Authentication authentication) {
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        return (headerUserId != null && !headerUserId.isBlank()) ? headerUserId : "ADMIN";
    }
}
