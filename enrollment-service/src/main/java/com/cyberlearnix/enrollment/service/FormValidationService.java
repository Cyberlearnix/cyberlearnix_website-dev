package com.cyberlearnix.enrollment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FormValidationService {

    private final ObjectMapper objectMapper;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    public void validateResponse(String fieldsJson, String submissionDataJson) throws Exception {
        if (fieldsJson == null || fieldsJson.isBlank()) return;
        if (submissionDataJson == null || submissionDataJson.isEmpty()) return;

        List<Map<String, Object>> fields = objectMapper.readValue(fieldsJson, new TypeReference<List<Map<String, Object>>>() {});
        Map<String, Object> data = objectMapper.readValue(submissionDataJson, new TypeReference<Map<String, Object>>() {});

        for (Map<String, Object> field : fields) {
            String label = (String) field.get("label");
            String id = (String) field.get("id");
            String name = (String) field.get("name");
            
            // Get field type (check both 'field_type' and 'type')
            String type = (String) field.get("field_type");
            if (type == null) type = (String) field.get("type");
            if (type == null) type = "text"; // Default to text to avoid NPE
            
            // Get required status (check both 'is_required' and 'required')
            boolean required = false;
            Object reqObj = field.get("is_required");
            if (reqObj == null) reqObj = field.get("required");
            
            if (reqObj != null) {
                if (reqObj instanceof Boolean) {
                    required = (Boolean) reqObj;
                } else if (reqObj instanceof String) {
                    required = Boolean.parseBoolean((String) reqObj);
                }
            }
            
            // Try lookup by: ID → name (frontend form builder uses name as key) → label (legacy fallback)
            Object value = data.get(id);
            if (value == null && name != null) value = data.get(name);
            if (value == null && label != null) value = data.get(label);

            if ("section_header".equals(type) || "paragraph".equals(type) || "html".equals(type)
                    || "declaration".equals(type) || "declaration_terms".equals(type)) continue;

            // Required check
            boolean isMissing = (value == null || String.valueOf(value).trim().isEmpty());
            if (required && isMissing) {
                throw new RuntimeException("Field '" + label + "' (ID: " + id + ") is required but missing in submission data.");
            }

            if (isMissing) continue;

            String valStr = String.valueOf(value);

            // Type specific validation
            switch (type) {
                case "email":
                    if (!EMAIL_PATTERN.matcher(valStr).matches()) {
                        throw new RuntimeException("Invalid email format for field '" + label + "'");
                    }
                    break;
                case "number":
                    try {
                        Double.parseDouble(valStr);
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Field '" + label + "' must be a number");
                    }
                    break;
                case "rating":
                    try {
                        int rating = Integer.parseInt(valStr);
                        Object optionsObj = field.get("options");
                        int maxStars = 5;
                        if (optionsObj instanceof List) {
                            List<?> optionsList = (List<?>) optionsObj;
                            if (!optionsList.isEmpty()) {
                                maxStars = Integer.parseInt(String.valueOf(optionsList.get(0)));
                            }
                        }
                        if (rating < 0 || rating > maxStars) {
                            throw new RuntimeException("Rating for '" + label + "' out of bounds (0-" + maxStars + ")");
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Invalid rating value for field '" + label + "'");
                    }
                    break;
                case "select":
                case "dropdown":
                case "radio":
                    Object opts = field.get("options");
                    if (opts instanceof List) {
                        validateOptions(label, valStr, (List<?>) opts);
                    }
                    break;
                case "checkbox":
                    Object availOptsObj = field.get("options");
                    if (availOptsObj instanceof List) {
                        List<?> availableOptions = (List<?>) availOptsObj;
                        if (value instanceof List) {
                            List<?> selectedOptions = (List<?>) value;
                            for (Object opt : selectedOptions) {
                                String optStr = String.valueOf(opt);
                                if (!listContainsString(availableOptions, optStr)) {
                                    throw new RuntimeException("Invalid option '" + optStr + "' for field '" + label + "'");
                                }
                            }
                        } else {
                            // Single value checkbox (could be comma separated or single string)
                            String[] selected = valStr.split(",");
                            for (String s : selected) {
                                String opt = s.trim();
                                if (!opt.isEmpty() && !listContainsString(availableOptions, opt)) {
                                    throw new RuntimeException("Invalid option '" + opt + "' for field '" + label + "'");
                                }
                            }
                        }
                    }
                    break;
                case "declaration":
                    if (required && !"true".equalsIgnoreCase(valStr) && !"on".equalsIgnoreCase(valStr)) {
                        throw new RuntimeException("You must accept the terms in field '" + label + "'");
                    }
                    break;
                default:
                    // For "text", "tel", "date", "textarea" - no extra validation needed beyond required check
                    break;
            }
        }
    }

    private void validateOptions(String label, String value, List<?> options) {
        if (options == null || options.isEmpty()) return;
        boolean found = false;
        for (Object opt : options) {
            if (String.valueOf(opt).equals(value)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new RuntimeException("Invalid option for field '" + label + "': " + value);
        }
    }
    
    private boolean listContainsString(List<?> list, String value) {
        for (Object item : list) {
            if (String.valueOf(item).equals(value)) return true;
        }
        return false;
    }
}
