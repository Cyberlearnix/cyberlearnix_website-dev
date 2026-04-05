package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.course.ContentReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContentReviewRepository extends JpaRepository<ContentReview, Long> {
    List<ContentReview> findByReviewStatus(String reviewStatus);
    List<ContentReview> findByTeacherId(Long teacherId);
    List<ContentReview> findByReviewerId(Long reviewerId);
    List<ContentReview> findByContentType(String contentType);
}
