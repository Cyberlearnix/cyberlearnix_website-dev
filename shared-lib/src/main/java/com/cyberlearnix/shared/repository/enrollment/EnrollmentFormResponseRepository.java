package com.cyberlearnix.shared.repository.enrollment;

import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import org.springframework.data.jpa.repository.JpaRepository;
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
    
    @org.springframework.data.jpa.repository.Query("SELECT SUM(r.amountPaid) FROM EnrollmentFormResponse r WHERE LOWER(r.paymentStatus) IN ('success', 'approved', 'paid')")
    Double calculateTotalRevenue();

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(r) FROM EnrollmentFormResponse r WHERE LOWER(r.paymentStatus) IN ('success', 'approved', 'paid')")
    long countPaidOrders();
}
