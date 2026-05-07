package com.cyberlearnix.enrollment.controller;

import com.cyberlearnix.shared.entity.enrollment.Coupon;
import com.cyberlearnix.enrollment.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for coupon / discount management.
 *
 * Admin endpoints (require JWT):
 *   POST   /api/enrollments/coupons           — create coupon
 *   GET    /api/enrollments/coupons           — list all coupons
 *   DELETE /api/enrollments/coupons/{id}      — deactivate coupon
 *
 * Public endpoint (no auth):
 *   POST   /api/enrollments/coupons/validate  — validate code and preview discount
 */
@RestController
@RequestMapping("/api/enrollments/coupons")
public class CouponController {

    private static final String KEY_MESSAGE = "message";

    @Autowired
    private CouponService couponService;

    // ── Admin: create ─────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody Map<String, Object> body) {
        try {
            String code = (String) body.get("code");
            String description = (String) body.getOrDefault("description", "");
            String typeStr = (String) body.get("discountType");
            Double value = body.get("discountValue") != null
                    ? ((Number) body.get("discountValue")).doubleValue() : null;
            Integer maxUsages = body.get("maxUsages") != null
                    ? ((Number) body.get("maxUsages")).intValue() : null;
            LocalDateTime expiresAt = null;
            if (body.get("expiresAt") != null) {
                expiresAt = LocalDateTime.parse((String) body.get("expiresAt"));
            }

            if (code == null || code.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Coupon code is required."));
            }
            if (typeStr == null || value == null) {
                return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "discountType and discountValue are required."));
            }

            Coupon.DiscountType type = Coupon.DiscountType.valueOf(typeStr.toUpperCase());
            Coupon saved = couponService.createCoupon(code, description, type, value, maxUsages, expiresAt);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(KEY_MESSAGE, e.getMessage()));
        }
    }

    // ── Admin: list ───────────────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<Coupon>> list() {
        return ResponseEntity.ok(couponService.listAll());
    }

    // ── Admin: deactivate ─────────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deactivate(@PathVariable Long id) {
        try {
            couponService.deactivate(id);
            return ResponseEntity.ok(Map.of(KEY_MESSAGE, "Coupon deactivated."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, e.getMessage()));
        }
    }

    // ── Public: validate ──────────────────────────────────────────────────────

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        double orderTotal = body.get("orderTotal") != null
                ? ((Number) body.get("orderTotal")).doubleValue() : 0.0;
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, KEY_MESSAGE, "Coupon code is required."));
        }
        Map<String, Object> result = couponService.validate(code, orderTotal);
        return ResponseEntity.ok(result);
    }
}
