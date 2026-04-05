package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.user.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {
    Optional<BlacklistedToken> findByToken(String token);
    void deleteByExpiryDateBefore(java.time.LocalDateTime now);
}
