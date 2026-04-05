package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.cms.PageComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PageComponentRepository extends JpaRepository<PageComponent, Long> {
    List<PageComponent> findBySectionIdOrderByOrderIndexAsc(Long sectionId);
}
