package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.PageSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PageSectionRepository extends JpaRepository<PageSection, Long> {
    List<PageSection> findByPageIdOrderByOrderIndexAsc(Long pageId);
}
