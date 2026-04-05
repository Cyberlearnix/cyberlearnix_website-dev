package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.course.ContentPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentPartnerRepository extends JpaRepository<ContentPartner, Long> {
}
