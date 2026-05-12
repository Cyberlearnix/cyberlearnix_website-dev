package com.cyberlearnix.shared.repository.course;

import com.cyberlearnix.shared.entity.course.CourseAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CourseAssignmentRepository extends JpaRepository<CourseAssignment, Long> {
    
    List<CourseAssignment> findByDueDateAfterOrderByDueDateAsc(LocalDateTime today);
    
    List<CourseAssignment> findByCourseIdInOrderByDueDateAsc(List<Long> courseIds);
}
