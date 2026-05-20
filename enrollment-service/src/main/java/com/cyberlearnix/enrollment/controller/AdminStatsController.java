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
    private static final String KEY_TOTAL_ENROLLED = "totalEnrolled";
    private static final String KEY_TOTAL_ENROLLMENTS = "totalEnrollments";

    private final EnrollmentFormResponseRepository responseRepository;
    private final EnrollmentFormConfigRepository configRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseServiceClient courseServiceClient;

    public AdminStatsController(
            EnrollmentFormResponseRepository responseRepository,
            EnrollmentFormConfigRepository configRepository,
            EnrollmentRepository enrollmentRepository,
            CourseServiceClient courseServiceClient) {
        this.responseRepository = responseRepository;
        this.configRepository = configRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseServiceClient = courseServiceClient;
    }

    // ─── Legacy endpoint (kept for backward compat) ──────────────────────────
    @GetMapping("/api/admin/stats/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRevenueStats() {
        Double rawRevenue = responseRepository.calculateTotalRevenue();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRevenue", rawRevenue != null ? rawRevenue : 0.0);
        stats.put("paidOrders", responseRepository.countPaidOrders());
        stats.put(KEY_TOTAL_ENROLLMENTS, enrollmentRepository.count());
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
        }).toList();

        // Top coupons
        List<Object[]> couponRows = responseRepository.topCouponsByRevenue();
        List<Map<String, Object>> topCoupons = couponRows.stream().map(row -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("couponCode", row[0]);
            c.put("usageCount", ((Number) row[1]).longValue());
            c.put("totalSavings", toDouble(row[2]));
            c.put("avgSaving", toDouble(row[3]));
            return c;
        }).toList();

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
        result.put(KEY_TOTAL_ENROLLMENTS, totalEnrollments);
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
        List<EnrollmentFormConfig> configs = configRepository.findByDeletedAtIsNull();
        Map<Long, List<String>> courseToForms = buildCourseToFormsMap(configs);

        List<Object[]> revenueRows = responseRepository.revenueByForm();
        Map<String, long[]> formRevMap = new HashMap<>();
        Map<String, Double> formSumMap = new HashMap<>();
        for (Object[] row : revenueRows) {
            String fid = (String) row[0];
            formRevMap.put(fid, new long[]{((Number) row[1]).longValue()});
            formSumMap.put(fid, toDouble(row[2]));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<String>> entry : courseToForms.entrySet()) {
            result.add(buildCourseEntry(entry.getKey(), entry.getValue(), configs, formRevMap, formSumMap));
        }
        result.sort((a, b) -> Long.compare(
                ((Number) b.get(KEY_TOTAL_ENROLLED)).longValue(),
                ((Number) a.get(KEY_TOTAL_ENROLLED)).longValue()));
        return ResponseEntity.ok(result);
    }

    private Map<Long, List<String>> buildCourseToFormsMap(List<EnrollmentFormConfig> configs) {
        Map<Long, List<String>> courseToForms = new LinkedHashMap<>();
        for (EnrollmentFormConfig cfg : configs) {
            if (cfg.getCourseId() != null) {
                courseToForms.computeIfAbsent(cfg.getCourseId(), k -> new ArrayList<>()).add(cfg.getId());
            }
        }
        return courseToForms;
    }

    private Map<String, Object> buildCourseEntry(Long courseId, List<String> formIds,
            List<EnrollmentFormConfig> configs, Map<String, long[]> formRevMap, Map<String, Double> formSumMap) {
        EnrollmentFormConfig primaryConfig = configs.stream()
                .filter(c -> courseId.equals(c.getCourseId())).findFirst().orElse(null);
        double coursePrice = primaryConfig != null && primaryConfig.getPaymentAmount() != null
                ? primaryConfig.getPaymentAmount() : 0.0;

        long totalEnrolled = 0;
        double totalRevenue = 0.0;
        long paidCount = 0;
        for (String fid : formIds) {
            if (formRevMap.containsKey(fid)) {
                paidCount += formRevMap.get(fid)[0];
                totalRevenue += formSumMap.getOrDefault(fid, 0.0);
            }
            totalEnrolled += responseRepository.findByFormId(fid).size();
        }

        double totalListPrice = coursePrice * paidCount;
        double totalDiscounts = Math.max(0.0, totalListPrice - totalRevenue);
        double avgPayment = paidCount > 0 ? totalRevenue / paidCount : 0.0;
        String[] courseInfo = fetchCourseInfo(courseId);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", courseId);
        row.put("title", courseInfo[0]);
        row.put("coursePrice", coursePrice);
        row.put("difficulty", courseInfo[1]);
        row.put("isPublished", Boolean.parseBoolean(courseInfo[2]));
        row.put(KEY_TOTAL_ENROLLED, totalEnrolled);
        row.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
        row.put("totalDiscounts", Math.round(totalDiscounts * 100.0) / 100.0);
        row.put("totalListPrice", Math.round(totalListPrice * 100.0) / 100.0);
        row.put("avgPayment", Math.round(avgPayment * 100.0) / 100.0);
        row.put("freeEnrollments", totalEnrolled - paidCount);
        row.put("paidEnrollments", paidCount);
        row.put("discountEnabled", primaryConfig != null && primaryConfig.isDiscountEnabled());
        row.put("discountType", primaryConfig != null ? primaryConfig.getDiscountType() : null);
        row.put("discountValue", primaryConfig != null ? primaryConfig.getDiscountValue() : null);
        row.put("couponCode", primaryConfig != null ? primaryConfig.getDiscountCouponCode() : null);
        return row;
    }

    private String[] fetchCourseInfo(Long courseId) {
        String title = "Course #" + courseId;
        String difficulty = null;
        String published = "false";
        try {
            Map<String, Object> info = courseServiceClient.getCourseInfo(courseId);
            if (info != null) {
                title = info.getOrDefault("title", title).toString();
                if (info.get("difficulty") != null) difficulty = info.get("difficulty").toString();
                Object pub = info.get("isPublished");
                if (pub == null) pub = info.get("published");
                if (pub instanceof Boolean b) published = b.toString();
            }
        } catch (Exception e) {
            log.warn("Could not fetch course info for id={}: {}", courseId, e.getMessage());
        }
        return new String[]{title, difficulty, published};
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
        result.put(KEY_TOTAL_ENROLLMENTS, totalEnrollments);
        result.put("totalEnrollments", totalEnrollments);
        return ResponseEntity.ok(result);
    }

    // ─── helper ───────────────────────────────────────────────────────────────
    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof BigDecimal bd) return bd.doubleValue();
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0.0; }
    }
}
