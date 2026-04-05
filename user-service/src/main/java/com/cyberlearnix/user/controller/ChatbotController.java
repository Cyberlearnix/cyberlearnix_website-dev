package com.cyberlearnix.user.controller;

import com.cyberlearnix.shared.entity.user.ChatbotResponse;
import com.cyberlearnix.shared.repository.ChatbotResponseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotResponseRepository chatbotResponseRepository;

    public ChatbotController(ChatbotResponseRepository chatbotResponseRepository) {
        this.chatbotResponseRepository = chatbotResponseRepository;
    }

    @PostConstruct
    public void init() {
        initializeDefaultResponses();
    }

    private void initializeDefaultResponses() {
        if (chatbotResponseRepository.count() == 0) {
            chatbotResponseRepository.save(createResponse("greeting", "Hello! Welcome to Cyberlearnix. How can I help you today?", "general", 0.8, false));
            chatbotResponseRepository.save(createResponse("courses", "We offer a variety of cybersecurity courses including Ethical Hacking, Network Security, and Digital Forensics. Would you like to see our course catalog?", "courses", 0.7, true));
            chatbotResponseRepository.save(createResponse("pricing", "Our courses range from $49 to $299 depending on the level and duration. We also offer payment plans and scholarships. Would you like more details?", "pricing", 0.7, true));
            chatbotResponseRepository.save(createResponse("support", "You can reach our support team via email at admin@cyberlearnix.com or through the contact form on our website.", "support", 0.7, false));
            chatbotResponseRepository.save(createResponse("enrollment", "To enroll in a course, simply browse our catalog, select a course, and click 'Enroll Now'. You'll need to create an account if you haven't already.", "enrollment", 0.7, true));
        }
    }

    private ChatbotResponse createResponse(String intent, String response, String category, double confidence, boolean requiresFollowup) {
        ChatbotResponse cr = new ChatbotResponse();
        cr.setIntent(intent);
        cr.setResponse(response);
        cr.setCategory(category);
        cr.setConfidenceThreshold(confidence);
        cr.setRequiresFollowup(requiresFollowup);
        cr.setIsActive(true);
        return cr;
    }

    @GetMapping
    public ResponseEntity<List<ChatbotResponse>> getAllResponses(@RequestParam(required = false) String category) {
        if (category != null) {
            return ResponseEntity.ok(chatbotResponseRepository.findByCategoryAndIsActiveTrue(category));
        }
        return ResponseEntity.ok(chatbotResponseRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ChatbotResponse> createResponse(@RequestBody ChatbotResponse response) {
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(chatbotResponseRepository.save(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChatbotResponse> updateResponse(@PathVariable Long id, @RequestBody ChatbotResponse updates) {
        Optional<ChatbotResponse> existing = chatbotResponseRepository.findById(id);
        if (existing.isPresent()) {
            ChatbotResponse response = existing.get();
            response.setIntent(updates.getIntent());
            response.setResponse(updates.getResponse());
            response.setCategory(updates.getCategory());
            response.setConfidenceThreshold(updates.getConfidenceThreshold());
            response.setRequiresFollowup(updates.getRequiresFollowup());
            response.setFollowupQuestions(updates.getFollowupQuestions());
            response.setTrainingPhrases(updates.getTrainingPhrases());
            response.setIsActive(updates.getIsActive());
            response.setUpdatedAt(LocalDateTime.now());
            return ResponseEntity.ok(chatbotResponseRepository.save(response));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteResponse(@PathVariable Long id) {
        chatbotResponseRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/train")
    public ResponseEntity<ChatbotResponse> trainResponse(@PathVariable Long id, @RequestBody Map<String, String> trainingData) {
        Optional<ChatbotResponse> existing = chatbotResponseRepository.findById(id);
        if (existing.isPresent()) {
            ChatbotResponse response = existing.get();
            response.setUsageCount(response.getUsageCount() + 1);
            response.setLastUsedAt(LocalDateTime.now());
            return ResponseEntity.ok(chatbotResponseRepository.save(response));
        }
        return ResponseEntity.notFound().build();
    }
}
