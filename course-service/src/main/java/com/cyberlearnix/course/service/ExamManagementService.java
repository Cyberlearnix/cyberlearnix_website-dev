package com.cyberlearnix.course.service;

import com.cyberlearnix.shared.entity.course.Exam;
import com.cyberlearnix.shared.entity.course.ExamAttempt;
import com.cyberlearnix.shared.entity.course.Question;
import com.cyberlearnix.shared.repository.course.ExamAttemptRepository;
import com.cyberlearnix.shared.repository.course.ExamRepository;
import com.cyberlearnix.shared.repository.course.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamManagementService {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final ExamAttemptRepository attemptRepository;

    private static final String FIELD_TITLE = "title";
    private static final String FIELD_NEGATIVE_MARKING = "negativeMarking";
    private static final String FIELD_NEGATIVE_MARKS = "negativeMarks";
    private static final String FIELD_LANGUAGE = "language";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_GRADED = "GRADED";

    // ─── Exam CRUD ────────────────────────────────────────────────────────────

    @Transactional
    public Exam createExam(Map<String, Object> payload, String userId) {
        Exam exam = buildExamFromPayload(new Exam(), payload);
        exam.setCreatedBy(userId);
        exam.setStatus("DRAFT");
        Exam saved = examRepository.save(exam);
        saveQuestionsFromPayload(saved.getId(), payload);
        return saved;
    }

    @Transactional
    public Exam updateExam(Long id, Map<String, Object> payload, String userId) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + id));
        buildExamFromPayload(exam, payload);
        Exam saved = examRepository.save(exam);
        // Replace questions if provided
        if (payload.containsKey("questions")) {
            questionRepository.deleteByExamId(id);
            saveQuestionsFromPayload(id, payload);
        }
        return saved;
    }

    @Transactional
    public Exam publishExam(Long id) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + id));
        exam.setStatus("PUBLISHED");
        return examRepository.save(exam);
    }

    @Transactional
    public Exam cloneExam(Long id, String userId) {
        Exam original = examRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + id));
        Exam clone = new Exam();
        clone.setTitle(original.getTitle() + " (Copy)");
        clone.setSubtitle(original.getSubtitle());
        clone.setDescription(original.getDescription());
        clone.setInstructions(original.getInstructions());
        clone.setCourseId(original.getCourseId());
        clone.setExamType(original.getExamType());
        clone.setDifficulty(original.getDifficulty());
        clone.setDurationMinutes(original.getDurationMinutes());
        clone.setTotalMarks(original.getTotalMarks());
        clone.setPassingMarks(original.getPassingMarks());
        clone.setStatus("DRAFT");
        clone.setMaxAttempts(original.getMaxAttempts());
        clone.setRandomizeQuestions(original.getRandomizeQuestions());
        clone.setRandomizeOptions(original.getRandomizeOptions());
        clone.setNegativeMarking(original.getNegativeMarking());
        clone.setNegativeMarkValue(original.getNegativeMarkValue());
        clone.setTabSwitchDetection(original.getTabSwitchDetection());
        clone.setCopyPasteBlocked(original.getCopyPasteBlocked());
        clone.setCreatedBy(userId);
        Exam saved = examRepository.save(clone);

        // Clone questions
        List<Question> origQuestions = questionRepository.findByExamIdOrderByOrderIndex(id);
        for (Question oq : origQuestions) {
            Question nq = new Question();
            nq.setExamId(saved.getId());
            nq.setQuestionType(oq.getQuestionType());
            nq.setQuestionText(oq.getQuestionText());
            nq.setOptions(oq.getOptions());
            nq.setCorrectAnswer(oq.getCorrectAnswer());
            nq.setExplanation(oq.getExplanation());
            nq.setMarks(oq.getMarks());
            nq.setNegativeMarks(oq.getNegativeMarks());
            nq.setDifficulty(oq.getDifficulty());
            nq.setOrderIndex(oq.getOrderIndex());
            nq.setTags(oq.getTags());
            nq.setLanguage(oq.getLanguage());
            nq.setStarterCode(oq.getStarterCode());
            nq.setCreatedBy(userId);
            questionRepository.save(nq);
        }
        saved.setQuestionCount(origQuestions.size());
        examRepository.save(saved);
        return saved;
    }

    public Map<String, Object> getExamForStudent(Long id) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + id));
        // Return exam without correct answers
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", exam.getId());
        result.put(FIELD_TITLE, exam.getTitle());
        result.put("subtitle", exam.getSubtitle());
        result.put("instructions", exam.getInstructions());
        result.put("durationMinutes", exam.getDurationMinutes());
        result.put("totalMarks", exam.getTotalMarks());
        result.put("passingMarks", exam.getPassingMarks());
        result.put("maxAttempts", exam.getMaxAttempts());
        result.put("questionCount", exam.getQuestionCount());
        result.put("examType", exam.getExamType());
        result.put(FIELD_NEGATIVE_MARKING, exam.getNegativeMarking());
        result.put("negativeMarkValue", exam.getNegativeMarkValue());
        result.put("tabSwitchDetection", exam.getTabSwitchDetection());
        result.put("webcamProctoring", exam.getWebcamProctoring());
        result.put("browserLockdown", exam.getBrowserLockdown());
        result.put("copyPasteBlocked", exam.getCopyPasteBlocked());
        result.put("maxViolations", exam.getMaxViolations());
        return result;
    }

    // ─── Attempt lifecycle ────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> startAttempt(Long examId, String studentId, String studentName, String ipAddress) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));

        long attemptCount = attemptRepository.countByExamIdAndStudentId(examId, studentId);
        if (attemptCount >= exam.getMaxAttempts()) {
            throw new RuntimeException("Maximum attempts (" + exam.getMaxAttempts() + ") reached for this exam");
        }

        ExamAttempt attempt = ExamAttempt.builder()
                .examId(examId)
                .studentId(studentId)
                .studentName(studentName)
                .attemptNumber((int) attemptCount + 1)
                .status(STATUS_IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .remainingSeconds(exam.getDurationMinutes() != null ? exam.getDurationMinutes() * 60 : 3600)
                .totalMarks(exam.getTotalMarks())
                .ipAddress(ipAddress)
                .tabSwitchCount(0)
                .violationCount(0)
                .build();
        ExamAttempt saved = attemptRepository.save(attempt);

        // Fetch questions, strip correct answers for student
        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(examId);
        if (Boolean.TRUE.equals(exam.getRandomizeQuestions())) {
            Collections.shuffle(questions);
        }
        List<Map<String, Object>> questionsForStudent = questions.stream()
                .map(this::stripCorrectAnswer)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("examId", examId);
        result.put("remainingSeconds", saved.getRemainingSeconds());
        result.put("questions", questionsForStudent);
        return result;
    }

    @Transactional
    public ExamAttempt saveAnswers(Long attemptId, String answersJson) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Attempt not found: " + attemptId));
        if (!STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new RuntimeException("Attempt is not in progress");
        }
        attempt.setAnswers(answersJson);
        return attemptRepository.save(attempt);
    }

    @Transactional
    public Map<String, Object> submitAttempt(Long attemptId, String answersJson) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Attempt not found: " + attemptId));
        if (!STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new RuntimeException("Attempt is not in progress");
        }

        attempt.setAnswers(answersJson);
        attempt.setStatus("SUBMITTED");
        attempt.setSubmittedAt(LocalDateTime.now());

        if (attempt.getStartedAt() != null) {
            attempt.setTimeSpentSeconds((int) ChronoUnit.SECONDS.between(attempt.getStartedAt(), attempt.getSubmittedAt()));
        }

        // Auto-grade objective questions
        Exam exam = examRepository.findById(attempt.getExamId()).orElse(null);
        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(attempt.getExamId());
        int autoScore = autoGrade(attempt, questions, answersJson, exam);
        attempt.setScore(autoScore);
        attempt.setTotalMarks(exam != null ? exam.getTotalMarks() : null);

        if (exam != null && exam.getTotalMarks() != null && exam.getTotalMarks() > 0) {
            double pct = ((double) autoScore / exam.getTotalMarks()) * 100;
            attempt.setPercentage(Math.round(pct * 10.0) / 10.0);
            attempt.setPassed(exam.getPassingMarks() != null && autoScore >= exam.getPassingMarks());
        }

        if (Boolean.TRUE.equals(exam != null && exam.getShowResultsImmediately())) {
            attempt.setStatus(STATUS_GRADED);
        }

        ExamAttempt saved = attemptRepository.save(attempt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attemptId", saved.getId());
        result.put("score", saved.getScore());
        result.put("totalMarks", saved.getTotalMarks());
        result.put("percentage", saved.getPercentage());
        result.put("passed", saved.getPassed());
        result.put("status", saved.getStatus());
        return result;
    }

    @Transactional
    public ExamAttempt gradeAttempt(Long attemptId, Map<String, Object> questionScores, String feedback, String graderId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Attempt not found: " + attemptId));
        attempt.setQuestionScores(questionScores.toString());
        attempt.setFeedback(feedback);
        attempt.setGradedBy(graderId);
        attempt.setGradedAt(LocalDateTime.now());
        attempt.setStatus(STATUS_GRADED);

        int total = questionScores.values().stream()
                .filter(v -> v instanceof Number)
                .mapToInt(v -> ((Number) v).intValue()).sum();
        attempt.setScore(total);
        if (attempt.getTotalMarks() != null && attempt.getTotalMarks() > 0) {
            double pct = ((double) total / attempt.getTotalMarks()) * 100;
            attempt.setPercentage(Math.round(pct * 10.0) / 10.0);
        }
        return attemptRepository.save(attempt);
    }

    // ─── Live monitoring ──────────────────────────────────────────────────────

    public List<Map<String, Object>> getLiveAttempts(Long examId) {
        List<ExamAttempt> live = attemptRepository.findLiveAttempts(examId);
        List<Question> questions = questionRepository.findByExamId(examId);
        int totalQ = questions.size();

        return live.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("studentId", a.getStudentId());
            m.put("studentName", a.getStudentName());
            m.put("status", a.getStatus());
            m.put("totalQuestions", totalQ);
            m.put("remainingMinutes", a.getRemainingSeconds() != null ? a.getRemainingSeconds() / 60 : 0);
            m.put("violationCount", a.getViolationCount());
            m.put("violations", a.getViolations());
            m.put("connected", true); // WebSocket would track this in real impl
            m.put("answeredCount", countAnswered(a.getAnswers()));
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void warnStudent(Long attemptId, String message) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Attempt not found: " + attemptId));
        log.info("Warning sent to student {} for attempt {}: {}", attempt.getStudentId(), attemptId, message);
        // In production: push via WebSocket / notification service
    }

    @Transactional
    public ExamAttempt extendTime(Long attemptId, int minutes) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Attempt not found: " + attemptId));
        int current = attempt.getRemainingSeconds() != null ? attempt.getRemainingSeconds() : 0;
        attempt.setRemainingSeconds(current + minutes * 60);
        return attemptRepository.save(attempt);
    }

    @Transactional
    public ExamAttempt terminateAttempt(Long attemptId, String reason) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Attempt not found: " + attemptId));
        attempt.setStatus("TERMINATED");
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setFeedback("Terminated by instructor: " + reason);
        return attemptRepository.save(attempt);
    }

    // ─── Analytics ────────────────────────────────────────────────────────────

    public Map<String, Object> getExamAnalytics(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));
        List<ExamAttempt> allAttempts = attemptRepository.findByExamId(examId);
        List<ExamAttempt> graded = allAttempts.stream()
                .filter(a -> "SUBMITTED".equals(a.getStatus()) || STATUS_GRADED.equals(a.getStatus()))
                .collect(Collectors.toList());

        long passed = graded.stream().filter(a -> Boolean.TRUE.equals(a.getPassed())).count();
        OptionalDouble avgPct = graded.stream()
                .filter(a -> a.getPercentage() != null)
                .mapToDouble(ExamAttempt::getPercentage).average();
        OptionalDouble maxPct = graded.stream()
                .filter(a -> a.getPercentage() != null)
                .mapToDouble(ExamAttempt::getPercentage).max();
        OptionalDouble minPct = graded.stream()
                .filter(a -> a.getPercentage() != null)
                .mapToDouble(ExamAttempt::getPercentage).min();
        OptionalDouble avgTime = graded.stream()
                .filter(a -> a.getTimeSpentSeconds() != null)
                .mapToInt(ExamAttempt::getTimeSpentSeconds).average();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", examId);
        result.put("examTitle", exam.getTitle());
        result.put("totalAttempts", allAttempts.size());
        result.put("submitted", graded.size());
        result.put("passCount", passed);
        result.put("failCount", graded.size() - passed);
        result.put("passRate", graded.isEmpty() ? 0.0 : Math.round((passed * 100.0 / graded.size()) * 10.0) / 10.0);
        result.put("avgScore", avgPct.isPresent() ? Math.round(avgPct.getAsDouble() * 10.0) / 10.0 : 0.0);
        result.put("highestScore", maxPct.isPresent() ? Math.round(maxPct.getAsDouble() * 10.0) / 10.0 : 0.0);
        result.put("lowestScore", minPct.isPresent() ? Math.round(minPct.getAsDouble() * 10.0) / 10.0 : 0.0);
        result.put("avgTimeMinutes", avgTime.isPresent() ? (int)(avgTime.getAsDouble() / 60) : 0);
        result.put("scoreDistribution", buildScoreDistribution(graded));
        return result;
    }

    public List<ExamAttempt> getExamResults(Long examId) {
        return attemptRepository.findByExamId(examId).stream()
                .filter(a -> "SUBMITTED".equals(a.getStatus()) || STATUS_GRADED.equals(a.getStatus()))
                .sorted(Comparator.comparing(ExamAttempt::getPercentage, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    // ─── Code execution (sandbox stub) ───────────────────────────────────────

    @SuppressWarnings("java:S1172")
    public Map<String, Object> runCode(String language, String code, String input, int timeLimitSeconds) {
        // Stub — in production integrate with Judge0, Piston, or custom sandbox
        log.info("Code execution requested: language={}, codeLength={}", language, code != null ? code.length() : 0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", "// Code execution sandbox not configured.\n// Output will appear here in production.");
        result.put("error", null);
        result.put("executionTime", 0);
        result.put("memoryUsed", 0);
        result.put("status", "SUCCESS");
        return result;
    }

    // ─── Question bank CRUD ───────────────────────────────────────────────────

    @Transactional
    public Question addBankQuestion(Map<String, Object> payload) {
        Question q = buildQuestionFromPayload(null, payload);
        q.setExamId(null);
        q.setIsFromBank(true);
        return questionRepository.save(q);
    }

    @Transactional
    public Question addQuestionToExam(Long examId, Map<String, Object> payload) {
        examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));
        Question q = buildQuestionFromPayload(null, payload);
        q.setExamId(examId);
        Question saved = questionRepository.save(q);
        // Update question count on exam
        long count = questionRepository.countByExamId(examId);
        examRepository.findById(examId).ifPresent(e -> { e.setQuestionCount((int) count); examRepository.save(e); });
        return saved;
    }

    @Transactional
    public Question updateQuestion(Long questionId, Map<String, Object> payload) {
        Question existing = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));
        buildQuestionFromPayload(existing, payload);
        return questionRepository.save(existing);
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));
        questionRepository.delete(q);
        if (q.getExamId() != null) {
            long count = questionRepository.countByExamId(q.getExamId());
            examRepository.findById(q.getExamId()).ifPresent(e -> { e.setQuestionCount((int) count); examRepository.save(e); });
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Exam buildExamFromPayload(Exam exam, Map<String, Object> p) {
        if (p.containsKey(FIELD_TITLE))               exam.setTitle((String) p.get(FIELD_TITLE));
        if (p.containsKey("subtitle"))            exam.setSubtitle((String) p.get("subtitle"));
        if (p.containsKey("description"))         exam.setDescription((String) p.get("description"));
        if (p.containsKey("instructions"))        exam.setInstructions((String) p.get("instructions"));
        if (p.containsKey("courseId"))            exam.setCourseId(toLong(p.get("courseId")));
        if (p.containsKey("subChapterId"))        exam.setSubChapterId(toLong(p.get("subChapterId")));
        if (p.containsKey("examType"))            exam.setExamType((String) p.get("examType"));
        if (p.containsKey("difficulty"))          exam.setDifficulty((String) p.get("difficulty"));
        if (p.containsKey("durationMinutes"))     exam.setDurationMinutes(toInt(p.get("durationMinutes")));
        if (p.containsKey("totalMarks"))          exam.setTotalMarks(toInt(p.get("totalMarks")));
        if (p.containsKey("passingMarks"))        exam.setPassingMarks(toInt(p.get("passingMarks")));
        if (p.containsKey("maxAttempts"))         exam.setMaxAttempts(toInt(p.get("maxAttempts")));
        if (p.containsKey("randomizeQuestions"))  exam.setRandomizeQuestions(toBool(p.get("randomizeQuestions")));
        if (p.containsKey("randomizeOptions"))    exam.setRandomizeOptions(toBool(p.get("randomizeOptions")));
        if (p.containsKey("showResultsImmediately")) exam.setShowResultsImmediately(toBool(p.get("showResultsImmediately")));
        if (p.containsKey(FIELD_NEGATIVE_MARKING))     exam.setNegativeMarking(toBool(p.get(FIELD_NEGATIVE_MARKING)));
        if (p.containsKey("negativeMarkValue"))   exam.setNegativeMarkValue(toDouble(p.get("negativeMarkValue")));
        if (p.containsKey("browserLockdown"))     exam.setBrowserLockdown(toBool(p.get("browserLockdown")));
        if (p.containsKey("webcamProctoring"))    exam.setWebcamProctoring(toBool(p.get("webcamProctoring")));
        if (p.containsKey("tabSwitchDetection"))  exam.setTabSwitchDetection(toBool(p.get("tabSwitchDetection")));
        if (p.containsKey("aiMonitoring"))        exam.setAiMonitoring(toBool(p.get("aiMonitoring")));
        if (p.containsKey("maxViolations"))       exam.setMaxViolations(toInt(p.get("maxViolations")));
        if (p.containsKey("copyPasteBlocked"))    exam.setCopyPasteBlocked(toBool(p.get("copyPasteBlocked")));
        if (p.containsKey("rightClickDisabled"))  exam.setRightClickDisabled(toBool(p.get("rightClickDisabled")));
        if (p.containsKey("fullscreenEnforced"))  exam.setFullscreenEnforced(toBool(p.get("fullscreenEnforced")));
        if (p.containsKey("ipLogging"))           exam.setIpLogging(toBool(p.get("ipLogging")));
        if (p.containsKey("sessionMonitoring"))   exam.setSessionMonitoring(toBool(p.get("sessionMonitoring")));
        if (p.containsKey("thumbnailUrl"))        exam.setThumbnailUrl((String) p.get("thumbnailUrl"));
        if (p.containsKey("tags"))                exam.setTags(p.get("tags") != null ? p.get("tags").toString() : null);
        if (p.containsKey("status"))              exam.setStatus((String) p.get("status"));
        return exam;
    }

    @SuppressWarnings("unchecked")
    private void saveQuestionsFromPayload(Long examId, Map<String, Object> payload) {
        Object qs = payload.get("questions");
        if (!(qs instanceof List)) return;
        List<Map<String, Object>> questionList = (List<Map<String, Object>>) qs;
        for (int i = 0; i < questionList.size(); i++) {
            Map<String, Object> qp = questionList.get(i);
            Question q = buildQuestionFromPayload(null, qp);
            q.setExamId(examId);
            q.setOrderIndex(i);
            questionRepository.save(q);
        }
    }

    private Question buildQuestionFromPayload(Question existing, Map<String, Object> p) {
        Question q = existing != null ? existing : new Question();
        applyQuestionAnswerFields(q, p);
        applyQuestionMetaFields(q, p);
        return q;
    }

    private void applyQuestionAnswerFields(Question q, Map<String, Object> p) {
        if (p.containsKey("questionType"))      q.setQuestionType((String) p.get("questionType"));
        if (p.containsKey("questionText"))      q.setQuestionText((String) p.get("questionText"));
        if (p.containsKey("options"))           q.setOptions(toJsonString(p.get("options")));
        if (p.containsKey("correctAnswer"))     q.setCorrectAnswer(toJsonString(p.get("correctAnswer")));
        if (p.containsKey("explanation"))       q.setExplanation((String) p.get("explanation"));
        if (p.containsKey("marks"))             q.setMarks(toDouble(p.get("marks")));
        if (p.containsKey(FIELD_NEGATIVE_MARKS)) q.setNegativeMarks(toDouble(p.get(FIELD_NEGATIVE_MARKS)));
        if (p.containsKey("difficulty"))        q.setDifficulty((String) p.get("difficulty"));
        if (p.containsKey("orderIndex"))        q.setOrderIndex(toInt(p.get("orderIndex")));
    }

    private void applyQuestionMetaFields(Question q, Map<String, Object> p) {
        if (p.containsKey("tags"))              q.setTags(p.get("tags") != null ? p.get("tags").toString() : null);
        if (p.containsKey("subject"))           q.setSubject((String) p.get("subject"));
        if (p.containsKey("topic"))             q.setTopic((String) p.get("topic"));
        if (p.containsKey(FIELD_LANGUAGE))      q.setLanguage((String) p.get(FIELD_LANGUAGE));
        if (p.containsKey("starterCode"))       q.setStarterCode((String) p.get("starterCode"));
        if (p.containsKey("testCases"))         q.setTestCases(toJsonString(p.get("testCases")));
        if (p.containsKey("timeLimitSeconds"))  q.setTimeLimitSeconds(toInt(p.get("timeLimitSeconds")));
        if (p.containsKey("imageUrl"))          q.setImageUrl((String) p.get("imageUrl"));
        if (p.containsKey("isActive"))          q.setIsActive(toBool(p.get("isActive")));
        if (p.containsKey("createdBy"))         q.setCreatedBy((String) p.get("createdBy"));
    }

    private Map<String, Object> stripCorrectAnswer(Question q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("questionType", q.getQuestionType());
        m.put("questionText", q.getQuestionText());
        m.put("options", q.getOptions());
        m.put("marks", q.getMarks());
        m.put(FIELD_NEGATIVE_MARKS, q.getNegativeMarks());
        m.put("difficulty", q.getDifficulty());
        m.put("orderIndex", q.getOrderIndex());
        m.put(FIELD_LANGUAGE, q.getLanguage());
        m.put("starterCode", q.getStarterCode());
        m.put("timeLimitSeconds", q.getTimeLimitSeconds());
        m.put("imageUrl", q.getImageUrl());
        return m;
    }

    private int autoGrade(ExamAttempt attempt, List<Question> questions, String answersJson, Exam exam) {
        // In production: parse JSON, compare answers per question type
        // For now return 0 — subjective questions require manual grading
        return 0;
    }

    private int countAnswered(String answersJson) {
        if (answersJson == null || answersJson.isBlank()) return 0;
        return (int) answersJson.chars().filter(c -> c == '"').count() / 2; // rough estimate
    }

    private List<Map<String, Object>> buildScoreDistribution(List<ExamAttempt> attempts) {
        String[][] ranges = {{"0–20", "0", "20"}, {"20–40", "20", "40"}, {"40–60", "40", "60"}, {"60–80", "60", "80"}, {"80–100", "80", "101"}};
        List<Map<String, Object>> dist = new ArrayList<>();
        for (String[] r : ranges) {
            double lo = Double.parseDouble(r[1]);
            double hi = Double.parseDouble(r[2]);
            long count = attempts.stream().filter(a -> a.getPercentage() != null && a.getPercentage() >= lo && a.getPercentage() < hi).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("range", r[0]);
            m.put("count", count);
            dist.add(m);
        }
        return dist;
    }

    private Long toLong(Object v) { return v == null ? null : Long.parseLong(v.toString()); }
    private Integer toInt(Object v) { return v == null ? null : Integer.parseInt(v.toString().split("\\.")[0]); }
    private Double toDouble(Object v) { return v == null ? null : Double.parseDouble(v.toString()); }
    private Boolean toBool(Object v) { return v == null ? null : Boolean.parseBoolean(v.toString()); }
    private String toJsonString(Object v) {
        if (v == null) return null;
        if (v instanceof String) return (String) v;
        return v.toString();
    }
}
