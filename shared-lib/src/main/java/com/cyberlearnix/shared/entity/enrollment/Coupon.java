package com.cyberlearnix.shared.entity.enrollment;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Admin-created discount coupon applicable to enrollment form payments.
 *
 * Discount types:
 *   PERCENTAGE — e.g. 20 = 20% off the total
 *   FLAT       — e.g. 500 = ₹500 off the total
 */
@Data
@Entity
@Table(name = "enrollment_coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public-facing code students enter, e.g. "WELCOME20". Stored uppercase. */
    @Column(unique = true, nullable = false, length = 50)
    private String code;

    /** Human-readable description shown in admin list. */
    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    /** Numeric value: 20 for 20%, 500 for ₹500 flat. */
    @Column(name = "discount_value", nullable = false)
    private Double discountValue;

    /** Maximum number of times this coupon may be used. null = unlimited. */
    @Column(name = "max_usages")
    private Integer maxUsages;

    /** How many times it has been used so far. */
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    /** Null = never expires. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Helpers ──────────────────────────────────────────────────────────────

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isUsageLimitReached() {
        return maxUsages != null && usageCount >= maxUsages;
    }

    /**
     * Compute the discount amount (in ₹) given the full order total.
     * For PERCENTAGE coupons the result is capped at the total so the net never goes negative.
     */
    public double computeDiscount(double total) {
        if (discountType == DiscountType.PERCENTAGE) {
            return Math.min(total, total * discountValue / 100.0);
        }
        return Math.min(total, discountValue);
    }

    public enum DiscountType {
        PERCENTAGE, FLAT
    }
}
