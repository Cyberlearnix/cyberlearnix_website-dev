package com.cyberlearnix.shared.repository.cms;

import com.cyberlearnix.shared.entity.cms.Recognition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecognitionRepository extends JpaRepository<Recognition, Long> {
}
