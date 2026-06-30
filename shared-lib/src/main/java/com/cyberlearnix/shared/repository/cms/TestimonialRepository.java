package com.cyberlearnix.shared.repository.cms;

import com.cyberlearnix.shared.entity.cms.Testimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestimonialRepository extends JpaRepository<Testimonial, Long> {
}
