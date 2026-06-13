package com.cyberlearnix.form.service;

import com.cyberlearnix.form.dto.FormAnalyticsDTO;
import com.cyberlearnix.form.dto.*;
import com.cyberlearnix.shared.entity.form.GeneralForm;
import com.cyberlearnix.shared.entity.form.GeneralFormResponse;
import com.cyberlearnix.shared.repository.form.GeneralFormRepository;
import com.cyberlearnix.shared.repository.form.GeneralFormResponseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FormService {

    private static final Logger log = LoggerFactory.getLogger(FormService.class);

    private final GeneralFormRepository formRepository;
    private final GeneralFormResponseRepository responseRepository;
    private final ObjectMapper objectMapper;
    private final FormValidationService validationService;
    private final com.cyberlearnix.form.client.NotificationClient notificationClient;

    public List<FormResponseDTO> getAllForms(String view) {
        List<GeneralForm> forms;
        if ("trash".equalsIgnoreCase(view)) {
            forms = formRepository.findAllByDeletedAtIsNotNull();
        } else {
            forms = formRepository.findAllByDeletedAtIsNull();
        }
        return forms.stream().map(this::mapToResponseDTO).collect(Collectors.toList());
    }

    public FormResponseDTO getForm(String id) {
        return mapToResponseDTO(getFormEntity(id));
    }

    private GeneralForm getFormEntity(String id) {
        return formRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Form not found or in trash"));
    }

    public FormResponseDTO getFormPublic(String id, String token) {
        // Find by ID first so we can give a proper 404 vs token-mismatch error
        GeneralForm form = formRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Form not found"));

        // Validate token only when the form has one stored (legacy forms without a token are accessible as-is)
        if (form.getToken() != null && !form.getToken().isEmpty()
                && (token == null || !form.getToken().equals(token))) {
            throw new jakarta.persistence.EntityNotFoundException("Form not found or invalid token");
        }

        if (!form.isActive()) {
            throw new RuntimeException("Form '" + form.getTitle() + "' is currently not accepting responses.");
        }

        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (form.getStartTime() != null && now.isBefore(form.getStartTime())) {
            throw new RuntimeException("Form '" + form.getTitle() + "' has not started yet (Starts at: " + form.getStartTime() + ")");
        }
        if (form.getEndTime() != null && now.isAfter(form.getEndTime())) {
            throw new RuntimeException("Form '" + form.getTitle() + "' has ended (Ended at: " + form.getEndTime() + ")");
        }

        return mapToResponseDTO(form);
    }

    @Transactional
    public FormResponseDTO createForm(FormRequestDTO dto) {
        GeneralForm form = new GeneralForm();
        form.setId(UUID.randomUUID().toString());
        form.setToken(UUID.randomUUID().toString());
        mapRequestToEntity(dto, form);
        form.setCreatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        form.setUpdatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        return mapToResponseDTO(formRepository.save(form));
    }

    @Transactional
    public FormResponseDTO updateForm(String id, FormRequestDTO dto) {
        GeneralForm existing = formRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Form not found or in trash"));
        mapRequestToEntity(dto, existing);
        existing.setUpdatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        return mapToResponseDTO(formRepository.save(existing));
    }

    @Transactional
    public void toggleActive(String id, boolean active) {
        GeneralForm form = formRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Form not found or in trash"));
        form.setActive(active);
        formRepository.save(form);
    }

    @Transactional
    public void deleteForm(String id) {
        GeneralForm form = formRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Form not found or in trash"));
        form.setDeletedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        formRepository.save(form);
    }

    @Transactional
    public void restoreForm(String id) {
        GeneralForm form = formRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Form not found"));
        form.setDeletedAt(null);
        formRepository.save(form);
    }

    @Transactional
    public void permanentDelete(String id) {
        GeneralForm form = formRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Form not found"));
        
        // Delete all responses first
        List<GeneralFormResponse> responses = responseRepository.findAllByFormId(id);
        responseRepository.deleteAll(responses);
        
        formRepository.delete(form);
    }

    @Transactional
    public FormResponseDTO duplicateForm(String id) {
        GeneralForm original = formRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new RuntimeException("Form not found or in trash"));
        
        GeneralForm duplicate = new GeneralForm();
        duplicate.setId(UUID.randomUUID().toString());
        duplicate.setTitle(original.getTitle() + " (Copy)");
        duplicate.setDescription(original.getDescription());
        duplicate.setFields(original.getFields());
        duplicate.setActive(false); // Copy should be inactive initially
        duplicate.setToken(UUID.randomUUID().toString());
        duplicate.setStartTime(original.getStartTime());
        duplicate.setEndTime(original.getEndTime());
        duplicate.setQuiz(original.isQuiz());
        duplicate.setQuizSettings(original.getQuizSettings());
        duplicate.setLimitOneResponse(original.isLimitOneResponse());
        duplicate.setPaymentEnabled(original.isPaymentEnabled());
        duplicate.setCourseId(original.getCourseId());
        duplicate.setPaymentAmount(original.getPaymentAmount());
        duplicate.setGstPercent(original.getGstPercent());
        duplicate.setGstAmount(original.getGstAmount());
        duplicate.setTotalAmount(original.getTotalAmount());
        duplicate.setCreatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        duplicate.setUpdatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        return mapToResponseDTO(formRepository.save(duplicate));
    }

    @Transactional
    public SubmissionResponseDTO submitResponse(String formId, SubmissionRequestDTO dto) {
        GeneralForm form = formRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Form not found"));

//        if (!form.isActive()) {
//            throw new RuntimeException("Form '" + form.getTitle() + "' (ID: " + formId + ") is currently closed or inactive.");
//        }

        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (form.getStartTime() != null && now.isBefore(form.getStartTime())) {
            throw new RuntimeException("Form '" + form.getTitle() + "' has not started yet (Starts at: " + form.getStartTime() + ")");
        }
        if (form.getEndTime() != null && now.isAfter(form.getEndTime())) {
            throw new RuntimeException("Form '" + form.getTitle() + "' has ended (Ended at: " + form.getEndTime() + ")");
        }

        String submissionDataJson;
        try {
            submissionDataJson = objectMapper.writeValueAsString(dto.getSubmissionData());
        } catch (Exception e) {
            throw new RuntimeException("Error serializing submission data: " + e.getMessage());
        }

        // Validate submission data
        try {
            validationService.validateResponse(form.getFields(), submissionDataJson);
        } catch (Exception e) {
            throw new RuntimeException("Validation failed: " + e.getMessage());
        }

        if (form.isLimitOneResponse() && dto.getUserEmail() != null) {
            if (responseRepository.findByFormIdAndUserEmail(formId, dto.getUserEmail()).isPresent()) {
                throw new RuntimeException("You have already responded to this form");
            }
        }

        GeneralFormResponse response = new GeneralFormResponse();
        response.setFormId(formId);
        response.setUserEmail(dto.getUserEmail());
        response.setSubmissionData(submissionDataJson);
        response.setCreatedAt(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        response.setPaymentStatus(form.isPaymentEnabled() ? "PENDING" : "NOT_REQUIRED");
        
        if (form.isQuiz()) {
            try {
                double totalScore = calculateQuizScore(form, response);
                response.setScore(totalScore);
            } catch (Exception e) {
                log.warn("Error calculating quiz score: {}", e.getMessage());
            }
        }
        
        GeneralFormResponse savedResponse = responseRepository.save(response);

        // Send confirmation email if user email is provided
        if (response.getUserEmail() != null && !response.getUserEmail().isEmpty()) {
            sendFormConfirmationEmail(response.getUserEmail(), form.getTitle(), response.getSubmissionData());
        }
        return mapToSubmissionResponseDTO(savedResponse);
    }

    private void sendFormConfirmationEmail(String userEmail, String formTitle, String submissionData) {
        try {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("recipientEmail", userEmail);
            data.put("formTitle", formTitle);
            data.put("responses", submissionData);
            notificationClient.sendNotification("send-form-confirmation", Map.of("data", data));
        } catch (Exception e) {
            log.warn("Failed to send confirmation email: {}", e.getMessage());
        }
    }

    public boolean hasAlreadyResponded(String formId, String email) {
        return responseRepository.findByFormIdAndUserEmail(formId, email).isPresent();
    }

    public List<SubmissionResponseDTO> getSubmissionResponses(String formId) {
        return responseRepository.findAllByFormIdAndDeletedAtIsNull(formId).stream()
                .map(this::mapToSubmissionResponseDTO)
                .toList();
    }

    private SubmissionResponseDTO mapToSubmissionResponseDTO(GeneralFormResponse entity) {
        if (entity == null) return null;
        try {
            return SubmissionResponseDTO.builder()
                    .id(entity.getId())
                    .formId(entity.getFormId())
                    .userEmail(entity.getUserEmail())
                    .submissionData(entity.getSubmissionData() != null ? objectMapper.readTree(entity.getSubmissionData()) : null)
                    .score(entity.getScore())
                    .createdAt(entity.getCreatedAt())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Error mapping submission to DTO: " + e.getMessage());
        }
    }

    private double calculateQuizScore(GeneralForm form, GeneralFormResponse response) throws Exception {
        if (form.getFields() == null) return 0;
        List<Map<String, Object>> fields = objectMapper.readValue(form.getFields(), new TypeReference<>() {});
        Map<String, Object> submissionData = objectMapper.readValue(response.getSubmissionData(), new TypeReference<>() {});
        
        double score = 0;
        for (Map<String, Object> field : fields) {
            String label = (String) field.get("label");
            String id = (String) field.get("id");
            String correctAnswer = String.valueOf(field.get("correct_answer"));
            
            Object pointsObj = field.get("points");
            double pointsValue = 0;
            if (pointsObj instanceof Number) {
                pointsValue = ((Number) pointsObj).doubleValue();
            } else if (pointsObj instanceof String) {
                try { pointsValue = Double.parseDouble((String) pointsObj); } catch (Exception ignored) {}
            }
            
            if (correctAnswer != null && !"null".equals(correctAnswer) && pointsValue > 0) {
                Object studentAnswer = submissionData.get(id);
                if (studentAnswer == null) studentAnswer = submissionData.get(label);
                
                if (studentAnswer != null && String.valueOf(studentAnswer).equalsIgnoreCase(correctAnswer)) {
                    score += pointsValue;
                }
            }
        }
        return score;
    }

    public FormAnalyticsDTO getAnalytics(String formId) {
        GeneralForm form = getFormEntity(formId);
        List<GeneralFormResponse> responses = responseRepository.findAllByFormIdAndDeletedAtIsNull(formId);

        try {
            List<Map<String, Object>> fields = objectMapper.readValue(form.getFields(), new TypeReference<>() {});
            List<FormAnalyticsDTO.QuestionAnalytics> questionAnalytics = new ArrayList<>();

            for (Map<String, Object> field : fields) {
                String label = (String) field.get("label");
                String type = (String) field.get("field_type");
                
                if ("section_header".equals(type)) continue;

                Map<String, Long> optionCounts = new HashMap<>();
                List<String> recentAnswers = new ArrayList<>();

                for (GeneralFormResponse resp : responses) {
                    Map<String, Object> data = objectMapper.readValue(resp.getSubmissionData(), new TypeReference<>() {});
                    Object val = data.get(label);
                    if (val != null) {
                        String strVal = String.valueOf(val);
                        optionCounts.merge(strVal, 1L, Long::sum);
                        if (recentAnswers.size() < 10) {
                            recentAnswers.add(strVal);
                        }
                    }
                }

                questionAnalytics.add(FormAnalyticsDTO.QuestionAnalytics.builder()
                        .label(label)
                        .fieldType(type)
                        .optionCounts(optionCounts)
                        .recentAnswers(recentAnswers)
                        .build());
            }

            double avgScore = responses.stream()
                    .filter(r -> r.getScore() != null)
                    .mapToDouble(GeneralFormResponse::getScore)
                    .average()
                    .orElse(0.0);

            return FormAnalyticsDTO.builder()
                    .formId(formId)
                    .totalResponses(responses.size())
                    .averageScore(form.isQuiz() ? avgScore : null)
                    .questions(questionAnalytics)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Error generating analytics: " + e.getMessage());
        }
    }

    public String exportResponsesToCsv(String formId) {
        GeneralForm form = getFormEntity(formId);
        List<GeneralFormResponse> responses = responseRepository.findAllByFormIdAndDeletedAtIsNull(formId);

        try {
            List<Map<String, Object>> fields = objectMapper.readValue(form.getFields(), new TypeReference<>() {});
            List<String> headers = new ArrayList<>();
            headers.add("Submission Date");
            headers.add("User Email");
            if (form.isQuiz()) headers.add("Score");

            for (Map<String, Object> field : fields) {
                if (!"section_header".equals(field.get("field_type"))) {
                    headers.add((String) field.get("label"));
                }
            }

            StringBuilder csv = new StringBuilder();
            csv.append(String.join(",", headers)).append("\n");

            for (GeneralFormResponse resp : responses) {
                Map<String, Object> data = objectMapper.readValue(resp.getSubmissionData(), new TypeReference<>() {});
                List<String> row = new ArrayList<>();
                row.add(resp.getCreatedAt().toString());
                row.add(resp.getUserEmail() != null ? resp.getUserEmail() : "Anonymous");
                if (form.isQuiz()) row.add(String.valueOf(resp.getScore()));

                for (Map<String, Object> field : fields) {
                    if (!"section_header".equals(field.get("field_type"))) {
                        String fLabel = (String) field.get("label");
                        String fId = (String) field.get("id");
                        Object val = data.get(fId);
                        if (val == null) val = data.get(fLabel);
                        
                        String cell = val != null ? String.valueOf(val).replace(",", ";") : "";
                        row.add(cell);
                    }
                }
                csv.append(String.join(",", row)).append("\n");
            }

            return csv.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error exporting CSV: " + e.getMessage());
        }
    }


    public List<SubmissionResponseDTO> getAllSubmissionResponses() {
        return responseRepository.findAll().stream()
                .map(this::mapToSubmissionResponseDTO)
                .collect(Collectors.toList());
    }

    private FormResponseDTO mapToResponseDTO(GeneralForm entity) {
        if (entity == null) return null;
        try {
            return FormResponseDTO.builder()
                    .id(entity.getId())
                    .title(entity.getTitle())
                    .description(entity.getDescription())
                    .fields(entity.getFields() != null ? objectMapper.readTree(entity.getFields()) : null)
                    .isActive(entity.isActive())
                    .token(entity.getToken())
                    .startTime(entity.getStartTime())
                    .endTime(entity.getEndTime())
                    .isQuiz(entity.isQuiz())
                    .quizSettings(entity.getQuizSettings() != null ? objectMapper.readTree(entity.getQuizSettings()) : null)
                    .limitOneResponse(entity.isLimitOneResponse())
                    .createdBy(entity.getCreatedBy())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .paymentEnabled(entity.isPaymentEnabled())
                    .courseId(entity.getCourseId())
                    .paymentAmount(entity.getPaymentAmount())
                    .gstPercent(entity.getGstPercent())
                    .gstAmount(entity.getGstAmount())
                    .totalAmount(entity.getTotalAmount())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Error mapping entity to DTO: " + e.getMessage());
        }
    }

    private void mapRequestToEntity(FormRequestDTO dto, GeneralForm entity) {
        try {
            entity.setTitle(dto.getTitle());
            entity.setDescription(dto.getDescription());
            entity.setFields(dto.getFields() != null ? objectMapper.writeValueAsString(dto.getFields()) : null);
            entity.setActive(dto.isActive());
            entity.setStartTime(dto.getStartTime());
            entity.setEndTime(dto.getEndTime());
            entity.setQuiz(dto.isQuiz());
            entity.setQuizSettings(dto.getQuizSettings() != null ? objectMapper.writeValueAsString(dto.getQuizSettings()) : null);
            entity.setLimitOneResponse(dto.isLimitOneResponse());
            entity.setPaymentEnabled(dto.isPaymentEnabled());
            entity.setCourseId(dto.getCourseId());
            entity.setPaymentAmount(dto.getPaymentAmount());
            entity.setGstPercent(dto.getGstPercent());
            entity.setGstAmount(dto.getGstAmount());
            entity.setTotalAmount(dto.getTotalAmount());
        } catch (Exception e) {
            throw new RuntimeException("Error serializing JSON fields: " + e.getMessage());
        }
    }
}
