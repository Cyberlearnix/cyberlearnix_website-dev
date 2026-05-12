package com.cyberlearnix.enrollment.service;

import com.cyberlearnix.enrollment.client.CourseServiceClient;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormConfig;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentService} covering BUG-002: txnid must be derived
 * from UUID.randomUUID(), not System.currentTimeMillis(), to eliminate collision
 * risk under concurrent payment initiations.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PaymentServiceTxnidTest {

    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private EnrollmentFormResponseRepository responseRepository;
    @Mock private EnrollmentFormConfigRepository configRepository;
    @Mock private CourseServiceClient courseServiceClient;
    @Mock private CouponService couponService;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void injectValueFields() {
        ReflectionTestUtils.setField(paymentService, "merchantKey", "test-key");
        ReflectionTestUtils.setField(paymentService, "merchantSalt", "test-salt");
        ReflectionTestUtils.setField(paymentService, "payuBaseUrl", "https://secure.payu.in");
        ReflectionTestUtils.setField(paymentService, "frontendUrl", "http://localhost:3000");
    }

    private EnrollmentFormResponse buildResponse() {
        EnrollmentFormResponse r = new EnrollmentFormResponse();
        r.setId(1L);
        r.setFormId("form-1");
        r.setStudentEmail("student@test.com");
        return r;
    }

    private EnrollmentFormConfig buildPaymentEnabledConfig() {
        EnrollmentFormConfig c = new EnrollmentFormConfig();
        c.setId("form-1");
        c.setTitle("Test Course");
        c.setPaymentEnabled(Boolean.TRUE);
        c.setPaymentAmount(999.0);
        // paymentCurrency defaults to "INR"; discountEnabled defaults to false
        return c;
    }

    // Guarantees: txnid starts with "TXN-" followed by exactly 16 uppercase hex chars (UUID-derived, not decimal)
    @Test
    void initiatePayment_txnid_matchesUuidBasedPattern() {
        when(responseRepository.findById(1L)).thenReturn(Optional.of(buildResponse()));
        when(configRepository.findById("form-1")).thenReturn(Optional.of(buildPaymentEnabledConfig()));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = paymentService.initiatePayment(
                1L, "Alice", "alice@test.com", "9876543210", null);

        String txnid = (String) result.get("txnid");
        // UUID-derived format: TXN-<16 uppercase hex chars>
        assertThat(txnid).matches("TXN-[0-9A-F]{16}")
                             .doesNotMatch("TXN-\\d{13}");
    }

    // Guarantees: two consecutive calls to initiatePayment produce distinct txnids (no collision)
    @Test
    void initiatePayment_txnid_isUniqueAcrossConsecutiveCalls() {
        when(responseRepository.findById(any())).thenReturn(Optional.of(buildResponse()));
        when(configRepository.findById("form-1")).thenReturn(Optional.of(buildPaymentEnabledConfig()));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String txnid1 = (String) paymentService
                .initiatePayment(1L, "Alice", "alice@test.com", "9876543210", null)
                .get("txnid");
        String txnid2 = (String) paymentService
                .initiatePayment(1L, "Bob", "bob@test.com", "9876543211", null)
                .get("txnid");

        assertThat(txnid1).isNotEqualTo(txnid2);
    }
}
