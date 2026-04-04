package com.cyberlearnix.user.service;

import com.cyberlearnix.shared.repository.BlacklistedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Scheduled(cron = "0 0 * * * *") // Run every hour at the top of the hour
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting cleanup of expired blacklisted tokens...");
        blacklistedTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
        log.info("Finished cleanup of expired blacklisted tokens.");
    }
}
