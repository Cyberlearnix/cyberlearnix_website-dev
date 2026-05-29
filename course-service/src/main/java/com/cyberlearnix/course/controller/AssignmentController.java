package com.cyberlearnix.course.controller;

import com.cyberlearnix.shared.entity.course.CourseAssignment;
import com.cyberlearnix.shared.repository.course.CourseAssignmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    @Autowired
    private CourseAssignmentRepository assignmentRepository;

    @GetMapping("/upcoming")
    public ResponseEntity<List<CourseAssignment>> getUpcomingAssignments(@RequestParam(required = false) List<Long> courseIds) {
        List<CourseAssignment> assignments;
        if (courseIds != null && !courseIds.isEmpty()) {
            assignments = assignmentRepository.findByCourseIdInOrderByDueDateAsc(courseIds);
        } else {
            assignments = assignmentRepository.findByDueDateAfterOrderByDueDateAsc(LocalDateTime.now());
        }
        return ResponseEntity.ok(assignments);
    }

    @PostMapping
    public ResponseEntity<CourseAssignment> createAssignment(@RequestBody CourseAssignment assignment) {
        assignment.setCreatedAt(LocalDateTime.now());
        assignment.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentRepository.save(assignment));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<CourseAssignment>> getAssignmentsByCourse(@PathVariable Long courseId) {
        List<CourseAssignment> assignments = assignmentRepository.findAll().stream()
                .filter(a -> a.getCourseId().equals(courseId))
                .toList();
        return ResponseEntity.ok(assignments);
    }
}
