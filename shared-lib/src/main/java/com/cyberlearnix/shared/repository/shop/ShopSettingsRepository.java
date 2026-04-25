package com.cyberlearnix.shared.repository.shop;

import com.cyberlearnix.shared.entity.shop.ShopSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ShopSettingsRepository extends JpaRepository<ShopSettings, Long> {
    Optional<ShopSettings> findFirstByOrderByIdAsc();
}
