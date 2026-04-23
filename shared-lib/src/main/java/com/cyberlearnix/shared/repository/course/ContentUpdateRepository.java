package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.ContentUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContentUpdateRepository extends JpaRepository<ContentUpdate, Long> {
}
