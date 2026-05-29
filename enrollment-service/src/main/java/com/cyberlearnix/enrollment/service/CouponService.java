package com.cyberlearnix.enrollment.service;

import com.cyberlearnix.shared.entity.enrollment.Coupon;
import com.cyberlearnix.shared.repository.enrollment.CouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CouponService {

    @Autowired
    private CouponRepository couponRepository;

    // ── Admin: create ─────────────────────────────────────────────────────────

    @Transactional
    public Coupon createCoupon(String code, String description,
                               Coupon.DiscountType discountType, Double discountValue,
                               Integer maxUsages, LocalDateTime expiresAt) {
        String upper = code.trim().toUpperCase();
        if (couponRepository.findByCodeIgnoreCase(upper).isPresent()) {
            throw new IllegalArgumentException("Coupon code already exists: " + upper);
        }
        Coupon c = new Coupon();
        c.setCode(upper);
        c.setDescription(description);
        c.setDiscountType(discountType);
        c.setDiscountValue(discountValue);
        c.setMaxUsages(maxUsages);
        c.setExpiresAt(expiresAt);
        return couponRepository.save(c);
    }

    // ── Admin: list ───────────────────────────────────────────────────────────

    public List<Coupon> listAll() {
        return couponRepository.findAllByOrderByCreatedAtDesc();
    }

    // ── Admin: deactivate ─────────────────────────────────────────────────────

    @Transactional
    public void deactivate(Long id) {
        Coupon c = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found: " + id));
        c.setActive(false);
        couponRepository.save(c);
    }

    // ── Student: validate (preview discount without consuming usage) ──────────

    /**
     * Returns a result map:
     * { valid, code, discountType, discountValue, discountAmount, finalAmount, message }
     */
    public Map<String, Object> validate(String code, double orderTotal) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code.trim().toUpperCase());
        result.put("orderTotal", orderTotal);

        String cleanCode = code != null ? code.trim() : "";
        var opt = couponRepository.findByCodeIgnoreCase(cleanCode);
        if (opt.isEmpty()) {
            result.put("valid", false);
            result.put("message", "Coupon code not found.");
            return result;
        }

        Coupon c = opt.get();

        if (!c.isActive()) {
            result.put("valid", false);
            result.put("message", "This coupon is no longer active.");
            return result;
        }
        if (c.isExpired()) {
            result.put("valid", false);
            result.put("message", "This coupon has expired.");
            return result;
        }
        if (c.isUsageLimitReached()) {
            result.put("valid", false);
            result.put("message", "This coupon has reached its usage limit.");
            return result;
        }

        double discount = c.computeDiscount(orderTotal);
        double finalAmount = Math.max(1.0, orderTotal - discount);

        result.put("valid", true);
        result.put("discountType", c.getDiscountType().name());
        result.put("discountValue", c.getDiscountValue());
        result.put("discountAmount", Math.round(discount * 100.0) / 100.0);
        result.put("finalAmount", Math.round(finalAmount * 100.0) / 100.0);
        result.put("message", "Coupon applied successfully!");
        return result;
    }

    /**
     * Validates the coupon and returns the discount amount without incrementing usage.
     */
    public double calculateDiscount(String code, double orderTotal) {
        Map<String, Object> v = validate(code, orderTotal);
        if (!Boolean.TRUE.equals(v.get("valid"))) {
            throw new IllegalArgumentException((String) v.get("message"));
        }
        return ((Number) v.get("discountAmount")).doubleValue();
    }

    /**
     * Increments the usage count of a coupon. Called only after successful payment confirmation.
     */
    @Transactional
    public void consumeCoupon(String code) {
        if (code == null || code.isBlank()) return;
        couponRepository.findByCodeIgnoreCase(code.trim().toUpperCase()).ifPresent(c -> {
            c.setUsageCount(c.getUsageCount() + 1);
            couponRepository.save(c);
        });
    }

    /**
     * Validates the coupon, increments its usage count, and returns the discount amount.
     * @deprecated Use calculateDiscount during initiation and consumeCoupon after payment success.
     */
    @Deprecated
    @Transactional
    public double applyAndConsume(String code, double orderTotal) {
        double discount = calculateDiscount(code, orderTotal);
        consumeCoupon(code);
        return discount;
    }
}
