package com.cyberlearnix.shared.entity.enrollment;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Tracks every PayU payment attempt linked to an enrollment form response.
 */
@Data
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @Column(name = "txnid", length = 40)
    private String txnid; // our generated transaction ID, e.g. TXN1714123456789

    // ── Link to form response ────────────────────────────────────────────────
    @Column(name = "form_response_id")
    private Long formResponseId;

    @Column(name = "form_id", length = 100)
    private String formId;

    // ── Student info (snapshot at payment time) ───────────────────────────────
    @Column(name = "student_email", length = 200)
    private String studentEmail;

    @Column(name = "student_name", length = 200)
    private String studentName;

    @Column(name = "student_phone", length = 20)
    private String studentPhone;

    // ── Amount ───────────────────────────────────────────────────────────────
    @Column(name = "amount")
    private Double amount;

    @Column(name = "currency", length = 10)
    private String currency = "INR";

    @Column(name = "product_info", length = 500)
    private String productInfo;

    // ── PayU response fields ──────────────────────────────────────────────────
    @Column(name = "payu_txnid", length = 100)
    private String payuTxnid; // PayU's own transaction ID from callback

    @Column(name = "mihpayid", length = 100)
    private String mihpayid; // PayU's unique payment ID

    @Column(name = "mode", length = 20)
    private String mode; // CC, DC, NB, UPI, etc.

    @Column(name = "status", length = 20)
    private String status = "PENDING"; // PENDING | SUCCESS | FAILURE | CANCELLED

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "bank_ref_num", length = 100)
    private String bankRefNum;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "discount_amount")
    private Double discountAmount;

    @Column(name = "hash_verified")
    private boolean hashVerified = false;

    // ── Timestamps ────────────────────────────────────────────────────────────
    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
