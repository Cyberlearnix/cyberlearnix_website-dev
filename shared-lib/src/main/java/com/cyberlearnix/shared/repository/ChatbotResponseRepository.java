package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.user.ChatbotResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatbotResponseRepository extends JpaRepository<ChatbotResponse, Long> {
    Optional<ChatbotResponse> findByIntent(String intent);
    List<ChatbotResponse> findByCategoryAndIsActiveTrue(String category);
    List<ChatbotResponse> findByIsActiveTrueOrderByUsageCountDesc();
}
