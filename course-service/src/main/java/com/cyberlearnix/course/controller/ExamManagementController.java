package com.cyberlearnix.course.controller;

import com.cyberlearnix.course.service.ExamManagementService;
import com.cyberlearnix.shared.entity.course.Exam;
import com.cyberlearnix.shared.entity.course.ExamAttempt;
import com.cyberlearnix.shared.entity.course.Question;
import com.cyberlearnix.shared.repository.course.ExamAttemptRepository;
import com.cyberlearnix.shared.repository.course.ExamRepository;
import com.cyberlearnix.shared.repository.course.QuestionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ExamManagementController {

    private final ExamManagementService examService;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final ExamAttemptRepository attemptRepository;

    private static final String KEY_ANSWERS = "answers";

    // ─── Exam CRUD ────────────────────────────────────────────────────────────

    @GetMapping("/exams")
    public ResponseEntity<List<Exam>> listExams(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        List<Exam> exams;
        if (courseId != null) exams = examRepository.findByCourseId(courseId);
        else if (status != null) exams = examRepository.findByStatus(status);
        else if ("INSTRUCTOR".equals(userRole) || "TEACHER".equals(userRole)) exams = examRepository.findByCreatedBy(userId);
        else exams = examRepository.findAll();
        return ResponseEntity.ok(exams);
    }

    @GetMapping("/exams/{id}")
    public ResponseEntity<?> getExam(@PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        if ("STUDENT".equals(userRole)) {
            return ResponseEntity.ok(examService.getExamForStudent(id));
        }
        return examRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/exams")
    public ResponseEntity<Exam> createExam(@RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Exam exam = examService.createExam(payload, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(exam);
    }

    @PutMapping("/exams/{id}")
    public ResponseEntity<Exam> updateExam(@PathVariable Long id, @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        Exam exam = examService.updateExam(id, payload, userId);
        return ResponseEntity.ok(exam);
    }

    @DeleteMapping("/exams/{id}")
    public ResponseEntity<Void> deleteExam(@PathVariable Long id) {
        examRepository.deleteById(id);
        questionRepository.deleteByExamId(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exams/{id}/publish")
    public ResponseEntity<Exam> publishExam(@PathVariable Long id) {
        return ResponseEntity.ok(examService.publishExam(id));
    }

    @PostMapping("/exams/{id}/clone")
    public ResponseEntity<Exam> cloneExam(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(examService.cloneExam(id, userId));
    }

    // ─── Questions ────────────────────────────────────────────────────────────

    @GetMapping("/exams/{id}/questions")
    public ResponseEntity<List<Question>> getQuestions(@PathVariable Long id) {
        return ResponseEntity.ok(questionRepository.findByExamIdOrderByOrderIndex(id));
    }

    @PostMapping("/exams/{id}/questions")
    public ResponseEntity<Question> addQuestion(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(examService.addQuestionToExam(id, payload));
    }

    @PutMapping("/exams/questions/{questionId}")
    public ResponseEntity<Question> updateQuestion(@PathVariable Long questionId, @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(examService.updateQuestion(questionId, payload));
    }

    @DeleteMapping("/exams/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long questionId) {
        examService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }

    // ─── Student exam flow ────────────────────────────────────────────────────

    @PostMapping("/exams/{id}/start")
    public ResponseEntity<Map<String, Object>> startAttempt(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String studentId,
            @RequestHeader(value = "X-User-Name", required = false) String studentName,
            HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null) ip = request.getRemoteAddr();
        Map<String, Object> result = examService.startAttempt(id, studentId, studentName, ip);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/exams/attempts/{attemptId}/answers")
    public ResponseEntity<Void> saveAnswers(@PathVariable Long attemptId, @RequestBody Map<String, Object> body) {
        String answersJson = body.containsKey(KEY_ANSWERS) ? body.get(KEY_ANSWERS).toString() : "{}";
        examService.saveAnswers(attemptId, answersJson);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exams/attempts/{attemptId}/submit")
    public ResponseEntity<Map<String, Object>> submitAttempt(@PathVariable Long attemptId,
            @RequestBody Map<String, Object> body) {
        String answersJson = body.containsKey(KEY_ANSWERS) ? body.get(KEY_ANSWERS).toString() : "{}";
        return ResponseEntity.ok(examService.submitAttempt(attemptId, answersJson));
    }

    @GetMapping("/exams/attempts/{attemptId}/result")
    public ResponseEntity<ExamAttempt> getResult(@PathVariable Long attemptId) {
        return attemptRepository.findById(attemptId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Live monitoring ──────────────────────────────────────────────────────

    @GetMapping("/exams/{id}/attempts/live")
    public ResponseEntity<List<Map<String, Object>>> getLiveAttempts(@PathVariable Long id) {
        return ResponseEntity.ok(examService.getLiveAttempts(id));
    }

    @PostMapping("/exams/attempts/{attemptId}/warn")
    public ResponseEntity<Void> warnStudent(@PathVariable Long attemptId, @RequestBody Map<String, Object> body) {
        String message = (String) body.getOrDefault("message", "Warning from instructor.");
        examService.warnStudent(attemptId, message);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exams/attempts/{attemptId}/extend")
    public ResponseEntity<ExamAttempt> extendTime(@PathVariable Long attemptId, @RequestBody Map<String, Object> body) {
        int minutes = Integer.parseInt(body.getOrDefault("minutes", 10).toString());
        return ResponseEntity.ok(examService.extendTime(attemptId, minutes));
    }

    @PostMapping("/exams/attempts/{attemptId}/terminate")
    public ResponseEntity<ExamAttempt> terminateAttempt(@PathVariable Long attemptId,
            @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? (String) body.getOrDefault("reason", "Terminated by instructor") : "Terminated by instructor";
        return ResponseEntity.ok(examService.terminateAttempt(attemptId, reason));
    }

    @PostMapping("/exams/attempts/{attemptId}/grade")
    public ResponseEntity<ExamAttempt> gradeAttempt(@PathVariable Long attemptId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String graderId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> scores = (Map<String, Object>) body.getOrDefault("questionScores", Map.of());
        String feedback = (String) body.getOrDefault("feedback", "");
        return ResponseEntity.ok(examService.gradeAttempt(attemptId, scores, feedback, graderId));
    }

    // ─── Analytics ────────────────────────────────────────────────────────────

    @GetMapping("/exams/{id}/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics(@PathVariable Long id) {
        return ResponseEntity.ok(examService.getExamAnalytics(id));
    }

    @GetMapping("/exams/{id}/results")
    public ResponseEntity<List<ExamAttempt>> getResults(@PathVariable Long id) {
        return ResponseEntity.ok(examService.getExamResults(id));
    }

    // ─── Code execution ───────────────────────────────────────────────────────

    @PostMapping("/exams/code/run")
    public ResponseEntity<Map<String, Object>> runCode(@RequestBody Map<String, Object> body) {
        String language = (String) body.getOrDefault("language", "python");
        String code = (String) body.getOrDefault("code", "");
        String input = (String) body.getOrDefault("input", "");
        int timeLimit = Integer.parseInt(body.getOrDefault("timeLimitSeconds", 5).toString());
        return ResponseEntity.ok(examService.runCode(language, code, input, timeLimit));
    }

    // ─── Question bank ────────────────────────────────────────────────────────

    @GetMapping("/questions")
    public ResponseEntity<Page<Question>> listQuestions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String subject) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        // In production: use a Specification/Criteria for dynamic filtering
        return ResponseEntity.ok(questionRepository.findAll(pageable));
    }

    @PostMapping("/questions")
    public ResponseEntity<Question> addToBank(@RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        payload.put("isFromBank", true);
        payload.put("isActive", true);
        payload.put("createdBy", userId);
        // Bank questions have no examId — addQuestionToExam with null examId is used for bank-only
        Question q = examService.addBankQuestion(payload);
        return ResponseEntity.ok(q);
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<Question> updateBankQuestion(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(examService.updateQuestion(id, payload));
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<Void> deleteBankQuestion(@PathVariable Long id) {
        examService.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/questions/import")
    public ResponseEntity<Map<String, Object>> importQuestions(@RequestBody List<Map<String, Object>> questions,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        int imported = 0;
        for (Map<String, Object> q : questions) {
            try {
                q.put("isFromBank", true);
                q.put("isActive", true);
                q.put("createdBy", userId);
                examService.addQuestionToExam(null, q);
                imported++;
            } catch (Exception e) {
                log.warn("Failed to import question: {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("imported", imported, "total", questions.size()));
    }

    @GetMapping("/questions/export")
    public ResponseEntity<List<Question>> exportQuestions() {
        return ResponseEntity.ok(questionRepository.findByIsActiveTrue());
    }
}
