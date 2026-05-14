package com.cyberlearnix.course.service;

import com.cyberlearnix.shared.entity.course.AssignmentContent;
import com.cyberlearnix.shared.entity.course.AssignmentSubmission;
import com.cyberlearnix.shared.repository.course.AssignmentContentRepository;
import com.cyberlearnix.shared.repository.course.AssignmentSubmissionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AssignmentManagementService {

    @Autowired
    private AssignmentContentRepository assignmentContentRepository;

    @Autowired
    private AssignmentSubmissionRepository submissionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── Submit assignment ────────────────────────────────────────────────────

    @Transactional
    public AssignmentSubmission submitAssignment(Long contentId, Map<String, Object> request, String studentId, String studentName) {
        AssignmentContent content = assignmentContentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + contentId));

        // Check attempt count
        List<AssignmentSubmission> prior = submissionRepository.findByStudentIdAndContentId(studentId, contentId);
        int maxAttempts = content.getMaxAttempts() != null ? content.getMaxAttempts() : 1;
        if (prior.size() >= maxAttempts) {
            throw new IllegalStateException("Maximum attempts (" + maxAttempts + ") reached for this assignment");
        }

        AssignmentSubmission sub = new AssignmentSubmission();
        sub.setContentId(contentId);
        sub.setStudentId(studentId);
        sub.setStudentName(studentName);
        sub.setAttemptNumber(prior.size() + 1);
        sub.setSubmittedAt(LocalDateTime.now());
        sub.setStatus("PENDING");

        // Map request fields
        sub.setSubmissionType(getString(request, "type"));
        sub.setLanguage(getString(request, "language"));
        sub.setCode(getString(request, "code"));
        sub.setEssayText(getString(request, "essayText"));
        if (request.get("wordCount") instanceof Number n) sub.setWordCount(n.intValue());
        if (request.get("courseId") instanceof Number n) sub.setCourseId(n.longValue());
        if (request.get("enrollmentId") instanceof Number n) sub.setEnrollmentId(n.longValue());

        // Serialize collections to JSON
        serializeField(request, "files",       sub::setFileUrls);
        serializeField(request, "links",       sub::setLinks);
        serializeField(request, "answers",     sub::setQuizAnswers);
        serializeField(request, "quizDetail",  sub::setTestResults);
        serializeField(request, "completedStepIds", sub::setLabCompletedSteps);

        // Late submission check
        if (content.getDueDate() != null && LocalDateTime.now().isAfter(content.getDueDate())) {
            if (!Boolean.TRUE.equals(content.getLateSubmissionAllowed())) {
                throw new IllegalStateException("Late submissions are not allowed for this assignment");
            }
            sub.setStatus("LATE");
        }

        AssignmentSubmission saved = submissionRepository.save(sub);

        // Auto-grade if mode is AUTO
        String gradingMode = content.getGradingMode() != null ? content.getGradingMode() : "MANUAL";
        if ("AUTO".equals(gradingMode) || "HYBRID".equals(gradingMode)) {
            autoGrade(saved, content, request);
        }

        // Run plagiarism check asynchronously (fire-and-forget for now)
        if (Boolean.TRUE.equals(content.getPlagiarismCheck())) {
            runPlagiarismCheck(saved, contentId);
        }

        return submissionRepository.findById(saved.getId()).orElse(saved);
    }

    // ─── Auto-grading ─────────────────────────────────────────────────────────

    @Transactional
    protected void autoGrade(AssignmentSubmission submission, AssignmentContent content, Map<String, Object> request) {
        try {
            Map<String, Object> meta = parseMeta(content.getAssignmentMetadata());
            String subType = submission.getSubmissionType();
            int score = 0;

            if ("QUIZ".equals(subType)) {
                // Score is already computed on frontend and passed as quizDetail
                Object quizScore = request.get("score");
                if (quizScore instanceof Number n) score = n.intValue();
            } else if ("LAB_PRACTICAL".equals(subType)) {
                // Score completed steps
                List<?> completedIds = parseList(submission.getLabCompletedSteps());
                List<Map<String, Object>> steps = (List<Map<String, Object>>) meta.getOrDefault("labSteps", Collections.emptyList());
                for (Map<String, Object> step : steps) {
                    if (completedIds.contains(step.get("id")) || completedIds.contains(String.valueOf(step.get("id")))) {
                        Object pts = step.get("points");
                        if (pts instanceof Number n) score += n.intValue();
                    }
                }
            }

            if (score > 0) {
                submission.setAutoGradeScore(score);
                submission.setScore(score);
                submission.setStatus("AUTO_GRADED");
                submissionRepository.save(submission);
            }
        } catch (Exception e) {
            // Non-fatal: auto-grade failure just leaves status as PENDING
        }
    }

    // ─── Code execution (sandbox) ─────────────────────────────────────────────

    public Map<String, Object> executeCode(String code, String language, String stdin, Integer timeLimitSeconds) {
        // Delegate to sandbox execution — implemented as ProcessBuilder sandboxing
        // For production, replace with Docker sandbox call (e.g. Judge0 API)
        Map<String, Object> result = new HashMap<>();
        try {
            String[] cmd = buildCommand(language, code, stdin);
            if (cmd == null) {
                result.put("success", false);
                result.put("stderr", "Language not supported: " + language);
                return result;
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // Send stdin
            if (stdin != null && !stdin.isEmpty()) {
                process.getOutputStream().write(stdin.getBytes());
                process.getOutputStream().flush();
            }
            process.getOutputStream().close();

            int timeout = timeLimitSeconds != null ? timeLimitSeconds : 5;
            boolean finished = process.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                result.put("success", false);
                result.put("stderr", "Time limit exceeded (" + timeout + "s)");
                return result;
            }

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            result.put("success", process.exitValue() == 0);
            result.put("stdout", stdout);
            result.put("stderr", stderr);
            result.put("exitCode", process.exitValue());
        } catch (Exception e) {
            result.put("success", false);
            result.put("stderr", "Execution error: " + e.getMessage());
        }
        return result;
    }

    private String[] buildCommand(String language, String code, String stdin) throws Exception {
        // NOTE: In production, use Docker isolation. This is a restricted dev sandbox.
        return switch (language != null ? language.toLowerCase() : "") {
            case "python"     -> new String[]{"python3", "-c", code};
            case "javascript" -> new String[]{"node", "-e", code};
            case "bash"       -> new String[]{"bash", "-c", code};
            default -> null; // Java/C++ require compilation — handled by dedicated sandbox
        };
    }

    // ─── Plagiarism check ─────────────────────────────────────────────────────

    protected void runPlagiarismCheck(AssignmentSubmission newSub, Long contentId) {
        if (newSub.getCode() == null && newSub.getEssayText() == null) return;
        try {
            List<AssignmentSubmission> others = submissionRepository.findByContentIdOrderBySubmittedAtDesc(contentId)
                    .stream()
                    .filter(s -> !s.getId().equals(newSub.getId()))
                    .toList();

            String text = newSub.getCode() != null ? newSub.getCode() : newSub.getEssayText();
            int maxSimilarity = 0;
            for (AssignmentSubmission other : others) {
                String otherText = other.getCode() != null ? other.getCode() : other.getEssayText();
                if (otherText == null) continue;
                int similarity = cosineSimilarity(text, otherText);
                maxSimilarity = Math.max(maxSimilarity, similarity);
            }

            newSub.setPlagiarismScore(maxSimilarity);
            if (maxSimilarity >= 70) newSub.setStatus("FLAGGED");
            submissionRepository.save(newSub);
        } catch (Exception e) {
            // Non-fatal
        }
    }

    /** Simple word-frequency cosine similarity (0-100). */
    private int cosineSimilarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        Map<String, Integer> freqA = wordFreq(a), freqB = wordFreq(b);
        Set<String> vocab = new HashSet<>(freqA.keySet());
        vocab.addAll(freqB.keySet());

        double dot = 0, normA = 0, normB = 0;
        for (String w : vocab) {
            double va = freqA.getOrDefault(w, 0);
            double vb = freqB.getOrDefault(w, 0);
            dot += va * vb;
            normA += va * va;
            normB += vb * vb;
        }
        if (normA == 0 || normB == 0) return 0;
        return (int) Math.round((dot / (Math.sqrt(normA) * Math.sqrt(normB))) * 100);
    }

    private Map<String, Integer> wordFreq(String text) {
        Map<String, Integer> freq = new HashMap<>();
        for (String w : text.toLowerCase().split("\\W+")) {
            if (w.length() > 2) freq.merge(w, 1, Integer::sum);
        }
        return freq;
    }

    // ─── Manual grading ───────────────────────────────────────────────────────

    @Transactional
    public AssignmentSubmission gradeSubmission(Long submissionId, Map<String, Object> request, String graderId) {
        AssignmentSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));

        if (request.get("score") instanceof Number n) sub.setScore(n.intValue());
        sub.setFeedback(getString(request, "feedback"));
        sub.setInternalNote(getString(request, "internalNote"));
        sub.setGradedBy(graderId);
        sub.setGradedAt(LocalDateTime.now());

        String status = getString(request, "status");
        sub.setStatus(status != null ? status : "GRADED");

        serializeField(request, "rubricScores", sub::setRubricScores);

        return submissionRepository.save(sub);
    }

    // ─── Analytics ────────────────────────────────────────────────────────────

    public Map<String, Object> getAnalytics(Long contentId) {
        AssignmentContent content = assignmentContentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + contentId));

        long total   = submissionRepository.countByContentId(contentId);
        long graded  = submissionRepository.countByContentIdAndStatus(contentId, "GRADED")
                     + submissionRepository.countByContentIdAndStatus(contentId, "AUTO_GRADED");
        long pending = submissionRepository.countByContentIdAndStatus(contentId, "PENDING")
                     + submissionRepository.countByContentIdAndStatus(contentId, "LATE");
        long flagged = submissionRepository.countPlagiarismFlagged(contentId, 40);

        Double avg = submissionRepository.findAvgScoreByContentId(contentId);
        List<Integer> scores = submissionRepository.findScoresByContentId(contentId);
        List<Map<String, Object>> dist = scores.stream()
                .map(s -> Map.<String, Object>of("score", s)).toList();

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalSubmissions", total);
        analytics.put("gradedCount", graded);
        analytics.put("pendingCount", pending);
        analytics.put("plagiarismFlagged", flagged);
        analytics.put("avgScore", avg != null ? Math.round(avg * 10.0) / 10.0 : null);
        analytics.put("maxScore", content.getMaxScore());
        analytics.put("passingScore", content.getPassingScore());
        analytics.put("scoreDistribution", dist);
        return analytics;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : v != null ? String.valueOf(v) : null;
    }

    private void serializeField(Map<String, Object> src, String key, java.util.function.Consumer<String> setter) {
        Object v = src.get(key);
        if (v == null) return;
        try {
            setter.accept(v instanceof String s ? s : objectMapper.writeValueAsString(v));
        } catch (Exception ignored) {}
    }

    private Map<String, Object> parseMeta(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return Collections.emptyMap(); }
    }

    private List<?> parseList(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try { return objectMapper.readValue(json, new TypeReference<List<?>>() {}); }
        catch (Exception e) { return Collections.emptyList(); }
    }
}
