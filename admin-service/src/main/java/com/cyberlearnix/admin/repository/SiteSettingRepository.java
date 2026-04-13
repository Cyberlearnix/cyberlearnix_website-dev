package com.cyberlearnix.admin.repository;

import com.cyberlearnix.admin.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {
    List<SiteSetting> findBySettingGroup(String settingGroup);
    Optional<SiteSetting> findBySettingKey(String settingKey);
}
