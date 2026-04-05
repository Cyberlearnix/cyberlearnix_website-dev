package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.cms.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, String> {
}
