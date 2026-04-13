package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.PromoBanner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromoBannerRepository extends JpaRepository<PromoBanner, Long> {
}
