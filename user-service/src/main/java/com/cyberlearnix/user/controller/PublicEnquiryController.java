package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.BusinessEnquiry;
import com.cyberlearnix.user.dto.BusinessEnquiryRequest;
import com.cyberlearnix.user.service.BusinessEnquiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/enquiries")
public class PublicEnquiryController {

    private final BusinessEnquiryService enquiryService;

    public PublicEnquiryController(BusinessEnquiryService enquiryService) {
        this.enquiryService = enquiryService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submitEnquiry(
            @Valid @RequestBody BusinessEnquiryRequest request,
            HttpServletRequest servletRequest) {

        String clientIp = extractClientIp(servletRequest);
        BusinessEnquiry saved = enquiryService.submitEnquiry(request, clientIp);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("id", saved.getId());
        response.put("enquiryReference", saved.getEnquiryReference());
        response.put("fullName", saved.getFullName());
        response.put("organizationName", saved.getOrganizationName());
        response.put("email", saved.getEmail());
        response.put("mobileNumber", saved.getMobileNumber());
        response.put("city", saved.getCity());
        response.put("enquiryType", saved.getEnquiryType());
        response.put("selectedServices", saved.getSelectedServices());
        response.put("createdAt", saved.getCreatedAt());
        response.put("message", "Thank You for Contacting Cyberlearnix! Your enquiry has been successfully submitted.");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String clientIp = request.getHeader("X-Client-IP");
        if (clientIp != null && !clientIp.isBlank()) {
            return clientIp.trim();
        }
        return request.getRemoteAddr();
    }
}
