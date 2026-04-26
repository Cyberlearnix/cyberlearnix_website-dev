package com.cyberlearnix.shared.repository.enrollment;

import com.cyberlearnix.shared.entity.enrollment.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {

    Optional<PaymentTransaction> findByTxnid(String txnid);

    List<PaymentTransaction> findByFormResponseId(Long formResponseId);

    List<PaymentTransaction> findByFormId(String formId);

    List<PaymentTransaction> findByStudentEmail(String studentEmail);

    Optional<PaymentTransaction> findTopByFormResponseIdOrderByInitiatedAtDesc(Long formResponseId);
}
