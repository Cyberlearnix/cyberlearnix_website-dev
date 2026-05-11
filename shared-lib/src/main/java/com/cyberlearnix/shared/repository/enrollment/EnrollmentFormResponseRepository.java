package com.cyberlearnix.shared.repository.enrollment;

import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EnrollmentFormResponseRepository extends JpaRepository<EnrollmentFormResponse, Long> {
    List<EnrollmentFormResponse> findByFormId(String formId);

    List<EnrollmentFormResponse> findByStudentEmail(String studentEmail);

    List<EnrollmentFormResponse> findByDeletedAtIsNull();

    List<EnrollmentFormResponse> findByDeletedAtIsNotNull();

    boolean existsByFormIdAndStudentEmailAndDeletedAtIsNull(String formId, String studentEmail);

    java.util.Optional<EnrollmentFormResponse> findByTransactionId(String transactionId);

    @Query("SELECT SUM(r.amountPaid) FROM EnrollmentFormResponse r WHERE LOWER(r.paymentStatus) IN ('success', 'approved', 'paid')")
    Double calculateTotalRevenue();

    @Query("SELECT COUNT(r) FROM EnrollmentFormResponse r WHERE LOWER(r.paymentStatus) IN ('success', 'approved', 'paid')")
    long countPaidOrders();

    /** Count responses with a non-null amountPaid > 0 that used a discount (discount field via form config).
     *  We approximate: any paid response whose amount < the form's paymentAmount has a discount applied. */
    @Query("SELECT COUNT(r) FROM EnrollmentFormResponse r WHERE LOWER(r.paymentStatus) IN ('success', 'approved', 'paid') AND r.amountPaid IS NOT NULL AND r.amountPaid > 0")
    long countPaidWithAmount();

    @Query("SELECT COUNT(r) FROM EnrollmentFormResponse r WHERE LOWER(r.paymentStatus) NOT IN ('success', 'approved', 'paid') AND r.deletedAt IS NULL")
    long countFreeOrPendingEnrollments();

    /** Per-form revenue summary: formId, count of paid, sum of amountPaid */
    @Query("SELECT r.formId, COUNT(r), COALESCE(SUM(r.amountPaid), 0) " +
           "FROM EnrollmentFormResponse r " +
           "WHERE LOWER(r.paymentStatus) IN ('success', 'approved', 'paid') " +
           "GROUP BY r.formId")
    List<Object[]> revenueByForm();

    /** Monthly revenue for last 12 months */
    @Query(value = "SELECT TO_CHAR(DATE_TRUNC('month', created_at), 'YYYY-MM') AS month, " +
                   "COUNT(*) AS enrollments, " +
                   "COALESCE(SUM(amount_paid), 0) AS revenue " +
                   "FROM enrollment_form_responses " +
                   "WHERE LOWER(payment_status) IN ('success', 'approved', 'paid') " +
                   "  AND created_at >= NOW() - INTERVAL '12 months' " +
                   "GROUP BY DATE_TRUNC('month', created_at) " +
                   "ORDER BY month DESC",
           nativeQuery = true)
    List<Object[]> monthlyRevenue();

    /** Top coupon codes by usage (via payment_mode or mihpayid is not the right field;
     *  coupon is stored in the PaymentTransaction entity, so we join via transaction_id) */
    @Query(value = "SELECT pt.coupon_code, COUNT(*) AS usage_count, " +
                   "COALESCE(SUM(pt.discount_amount), 0) AS total_savings, " +
                   "ROUND(COALESCE(AVG(pt.discount_amount), 0), 2) AS avg_saving " +
                   "FROM enrollment_form_responses r " +
                   "JOIN payment_transactions pt ON pt.txnid = r.transaction_id " +
                   "WHERE pt.coupon_code IS NOT NULL AND pt.coupon_code <> '' " +
                   "GROUP BY pt.coupon_code " +
                   "ORDER BY total_savings DESC " +
                   "LIMIT 10",
           nativeQuery = true)
    List<Object[]> topCouponsByRevenue();
}
