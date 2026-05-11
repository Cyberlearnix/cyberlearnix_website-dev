package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.enrollment.client.CourseServiceClient;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class AdminStatsController {

    private static final Logger log = LoggerFactory.getLogger(AdminStatsController.class);

    @Autowired
    private EnrollmentFormResponseRepository responseRepository;

    @Autowired
    private EnrollmentFormConfigRepository configRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseServiceClient courseServiceClient;

    // ─── Legacy endpoint (kept for backward compat) ──────────────────────────
    @GetMapping("/api/admin/stats/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRevenueStats() {
        Double rawRevenue = responseRepository.calculateTotalRevenue();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRevenue", rawRevenue != null ? rawRevenue : 0.0);
        stats.put("paidOrders", responseRepository.countPaidOrders());
        stats.put("totalEnrollments", enrollmentRepository.count());
        return ResponseEntity.ok(stats);
    }

    // ─── GET /api/admin/reports/revenue ──────────────────────────────────────
    @GetMapping("/api/admin/reports/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRevenueReport() {
        Double totalRevenue = responseRepository.calculateTotalRevenue();
        long paidOrders = responseRepository.countPaidOrders();
        long totalEnrollments = responseRepository.count();

        // Monthly stats
        List<Object[]> monthlyRows = responseRepository.monthlyRevenue();
        List<Map<String, Object>> monthlyStats = monthlyRows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", row[0]);
            m.put("enrollments", ((Number) row[1]).longValue());
            m.put("revenue", toDouble(row[2]));
            return m;
        }).collect(Collectors.toList());

        // Top coupons
        List<Object[]> couponRows = responseRepository.topCouponsByRevenue();
        List<Map<String, Object>> topCoupons = couponRows.stream().map(row -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("couponCode", row[0]);
            c.put("usageCount", ((Number) row[1]).longValue());
            c.put("totalSavings", toDouble(row[2]));
            c.put("avgSaving", toDouble(row[3]));
            return c;
        }).collect(Collectors.toList());

        // Discount totals: sum of discount_amount from payment_transactions via native coupon query
        double totalDiscounts = topCoupons.stream()
                .mapToDouble(c -> ((Number) c.get("totalSavings")).doubleValue())
                .sum();
        double totalListPrice = (totalRevenue != null ? totalRevenue : 0.0) + totalDiscounts;
        double avgOrderValue = paidOrders > 0
                ? (totalRevenue != null ? totalRevenue : 0.0) / paidOrders
                : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
        result.put("totalDiscounts", totalDiscounts);
        result.put("totalListPrice", totalListPrice);
        result.put("totalEnrollments", totalEnrollments);
        result.put("paidOrders", paidOrders);
        result.put("freeEnrollments", totalEnrollments - paidOrders);
        result.put("discountedEnrollments", couponRows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum());
        result.put("avgOrderValue", Math.round(avgOrderValue * 100.0) / 100.0);
        result.put("monthlyStats", monthlyStats);
        result.put("topCoupons", topCoupons);

        return ResponseEntity.ok(result);
    }

    // ─── GET /api/admin/reports/courses ──────────────────────────────────────
    @GetMapping("/api/admin/reports/courses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getCourseReport() {
        // Get all active forms that have a courseId
        List<EnrollmentFormConfig> configs = configRepository.findByDeletedAtIsNull();

        // Build map: courseId → list of formIds
        Map<Long, List<String>> courseToForms = new LinkedHashMap<>();
        for (EnrollmentFormConfig cfg : configs) {
            if (cfg.getCourseId() != null) {
                courseToForms.computeIfAbsent(cfg.getCourseId(), k -> new ArrayList<>()).add(cfg.getId());
            }
        }

        // Build map: formId → revenue row (formId, count, sum)
        List<Object[]> revenueRows = responseRepository.revenueByForm();
        Map<String, long[]> formRevMap = new HashMap<>(); // formId → [count, sumPaise]
        Map<String, Double> formSumMap = new HashMap<>(); // formId → sumRevenue
        for (Object[] row : revenueRows) {
            String fid = (String) row[0];
            long cnt = ((Number) row[1]).longValue();
            double rev = toDouble(row[2]);
            formRevMap.put(fid, new long[]{cnt});
            formSumMap.put(fid, rev);
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<Long, List<String>> entry : courseToForms.entrySet()) {
            Long courseId = entry.getKey();
            List<String> formIds = entry.getValue();

            // Aggregate across all forms for this course
            long totalEnrolled = 0;
            double totalRevenue = 0.0;
            long paidCount = 0;

            // Config-level data (use first form with payment enabled, or first form)
            EnrollmentFormConfig primaryConfig = configs.stream()
                    .filter(c -> courseId.equals(c.getCourseId()))
                    .findFirst().orElse(null);

            double coursePrice = primaryConfig != null && primaryConfig.getPaymentAmount() != null
                    ? primaryConfig.getPaymentAmount() : 0.0;
            boolean discountEnabled = primaryConfig != null && primaryConfig.isDiscountEnabled();
            String discountType = primaryConfig != null ? primaryConfig.getDiscountType() : null;
            Double discountValue = primaryConfig != null ? primaryConfig.getDiscountValue() : null;
            String couponCode = primaryConfig != null ? primaryConfig.getDiscountCouponCode() : null;

            for (String fid : formIds) {
                if (formRevMap.containsKey(fid)) {
                    paidCount += formRevMap.get(fid)[0];
                    totalRevenue += formSumMap.getOrDefault(fid, 0.0);
                }
                // Count all responses for this form (including free/pending)
                totalEnrolled += responseRepository.findByFormId(fid).size();
            }

            // Expected revenue at list price
            double totalListPrice = coursePrice * paidCount;
            double totalDiscounts = totalListPrice - totalRevenue;
            if (totalDiscounts < 0) totalDiscounts = 0.0;
            double avgPayment = paidCount > 0 ? totalRevenue / paidCount : 0.0;

            // Fetch course info from course-service
            String courseTitle = "Course #" + courseId;
            String difficulty = null;
            boolean isPublished = false;
            try {
                Map<String, Object> info = courseServiceClient.getCourseInfo(courseId);
                if (info != null) {
                    courseTitle = info.getOrDefault("title", courseTitle).toString();
                    if (info.get("difficulty") != null) difficulty = info.get("difficulty").toString();
                    Object pub = info.get("isPublished");
                    if (pub == null) pub = info.get("published");
                    if (pub instanceof Boolean) isPublished = (Boolean) pub;
                }
            } catch (Exception e) {
                log.warn("Could not fetch course info for id={}: {}", courseId, e.getMessage());
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", courseId);
            row.put("title", courseTitle);
            row.put("coursePrice", coursePrice);
            row.put("difficulty", difficulty);
            row.put("isPublished", isPublished);
            row.put("totalEnrolled", totalEnrolled);
            row.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
            row.put("totalDiscounts", Math.round(totalDiscounts * 100.0) / 100.0);
            row.put("totalListPrice", Math.round(totalListPrice * 100.0) / 100.0);
            row.put("avgPayment", Math.round(avgPayment * 100.0) / 100.0);
            row.put("freeEnrollments", totalEnrolled - paidCount);
            row.put("paidEnrollments", paidCount);
            row.put("discountEnabled", discountEnabled);
            row.put("discountType", discountType);
            row.put("discountValue", discountValue);
            row.put("couponCode", couponCode);
            result.add(row);
        }

        result.sort((a, b) -> Long.compare(
                ((Number) b.get("totalEnrolled")).longValue(),
                ((Number) a.get("totalEnrolled")).longValue()));

        return ResponseEntity.ok(result);
    }

    // ─── GET /api/admin/reports/users ─────────────────────────────────────────
    @GetMapping("/api/admin/reports/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserReport() {
        long totalEnrollments = enrollmentRepository.count();
        // Count distinct students via grouping
        long totalStudents = enrollmentRepository.findAll().stream()
                .map(e -> e.getStudentId())
                .filter(Objects::nonNull)
                .distinct()
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalStudents", totalStudents);
        result.put("totalEnrollments", totalEnrollments);
        return ResponseEntity.ok(result);
    }

    // ─── helper ───────────────────────────────────────────────────────────────
    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof BigDecimal) return ((BigDecimal) val).doubleValue();
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }
}
