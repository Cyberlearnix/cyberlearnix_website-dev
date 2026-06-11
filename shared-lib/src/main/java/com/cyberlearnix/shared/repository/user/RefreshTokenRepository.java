package com.cyberlearnix.shared.repository.user;

import com.cyberlearnix.shared.entity.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByToken(String token);
    @Modifying
    @Transactional
    void deleteByUserId(String userId);

    @Modifying
    @Transactional
    void deleteByToken(String token);
}
