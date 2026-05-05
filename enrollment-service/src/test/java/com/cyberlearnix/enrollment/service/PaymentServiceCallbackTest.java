package com.cyberlearnix.enrollment.service;

import com.cyberlearnix.enrollment.util.HashTestUtil;
import com.cyberlearnix.enrollment.client.CourseServiceClient;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.entity.enrollment.PaymentTransaction;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentService} covering callback hash verification,
 * webhook delegation, and payment status queries.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PaymentServiceCallbackTest {

    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private EnrollmentFormResponseRepository responseRepository;
    @Mock private EnrollmentFormConfigRepository configRepository;
    @Mock private CourseServiceClient courseServiceClient;
    @Mock private CouponService couponService;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void injectValueFields() {
        ReflectionTestUtils.setField(paymentService, "merchantKey",  "test-key");
        ReflectionTestUtils.setField(paymentService, "merchantSalt", "test-salt");
        ReflectionTestUtils.setField(paymentService, "payuBaseUrl",  "https://secure.payu.in");
        ReflectionTestUtils.setField(paymentService, "frontendUrl",  "http://localhost:3000");
        // handleWebhook delegates to self.handleCallback — inject real instance so it works without Spring context
        ReflectionTestUtils.setField(paymentService, "self", paymentService);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * Builds a params map whose hash is the CORRECT reverse hash for the given status.
     * Reverse hash format: salt|status|||||||||||email|firstname|productinfo|amount|txnid|key
     */
    private Map<String, String> buildCallbackParams(String status) {
        String reverseHashInput = "test-salt|" + status
                + "|||||||||||alice@test.com|Alice|Test Course|999.00|TXN-ABC|test-key";
        String hash = HashTestUtil.sha512(reverseHashInput);
        Map<String, String> params = new HashMap<>();
        params.put("status",      status);
        params.put("txnid",       "TXN-ABC");
        params.put("mihpayid",    "MIHPAY123");
        params.put("amount",      "999.00");
        params.put("productinfo", "Test Course");
        params.put("firstname",   "Alice");
        params.put("email",       "alice@test.com");
        params.put("hash",        hash);
        return params;
    }

    private PaymentTransaction buildPendingTxn() {
        PaymentTransaction txn = new PaymentTransaction();
        txn.setTxnid("TXN-ABC");
        txn.setStatus("PENDING");
        txn.setFormResponseId(10L);
        return txn;
    }

    private EnrollmentFormResponse buildFormResponse() {
        EnrollmentFormResponse r = new EnrollmentFormResponse();
        r.setId(10L);
        r.setFormId("form-1");
        r.setPaymentStatus("PENDING");
        r.setAmountPaid(0.0);  // must be non-null: PaymentService reads this on failure branch
        return r;
    }

    // ── Callback tests ────────────────────────────────────────────────────────

    // Guarantees: when status="success" and the reverse hash is valid, result.status is SUCCESS and hashVerified is true
    @Test
    void handleCallback_success_setsStatusSUCCESS_whenHashIsValid() {
        Map<String, String> params = buildCallbackParams("success");
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(buildPendingTxn()));
        when(responseRepository.findById(10L)).thenReturn(Optional.of(buildFormResponse()));

        Map<String, Object> result = paymentService.handleCallback(params);

        assertThat(result).containsEntry("status", "SUCCESS");
        assertThat(result).containsEntry("hashVerified", true);
    }

    // Guarantees: when status="failure" (with correct failure hash), result.status is FAILURE
    @Test
    void handleCallback_failure_setsStatusFAILURE_whenStatusIsFailure() {
        Map<String, String> params = buildCallbackParams("failure");
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(buildPendingTxn()));
        when(responseRepository.findById(10L)).thenReturn(Optional.of(buildFormResponse()));

        Map<String, Object> result = paymentService.handleCallback(params);

        assertThat(result).containsEntry("status", "FAILURE");
    }

    // Guarantees: when status="success" but hash is tampered, result.status is FAILURE and hashVerified is false
    @Test
    void handleCallback_setsStatusFAILURE_whenHashMismatch() {
        Map<String, String> params = buildCallbackParams("success");
        params.put("hash", "tampered_wrong_hash");
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(buildPendingTxn()));
        when(responseRepository.findById(10L)).thenReturn(Optional.of(buildFormResponse()));

        Map<String, Object> result = paymentService.handleCallback(params);

        assertThat(result).containsEntry("status", "FAILURE");
        assertThat(result).containsEntry("hashVerified", false);
    }

    // Guarantees: when the txnid is absent from DB, a new PaymentTransaction is created and saved with txnid from params
    @Test
    void handleCallback_createsNewTransaction_whenTxnidNotFound() {
        Map<String, String> params = buildCallbackParams("success");
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.empty());

        paymentService.handleCallback(params);

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTxnid()).isEqualTo("TXN-ABC");
    }

    // Guarantees: when payment fails, the linked EnrollmentFormResponse paymentStatus is updated to FAILED
    @Test
    void handleCallback_updatesFormResponse_toFAILED_whenPaymentFails() {
        Map<String, String> params = buildCallbackParams("failure");
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(buildPendingTxn()));
        EnrollmentFormResponse response = buildFormResponse();
        when(responseRepository.findById(10L)).thenReturn(Optional.of(response));

        paymentService.handleCallback(params);

        ArgumentCaptor<EnrollmentFormResponse> respCaptor = ArgumentCaptor.forClass(EnrollmentFormResponse.class);
        verify(responseRepository).save(respCaptor.capture());
        assertThat(respCaptor.getValue().getPaymentStatus()).isEqualTo("FAILED");
    }

    // Guarantees: handleWebhook delegates to handleCallback and the transaction is persisted (same flow as direct callback)
    @Test
    void handleWebhook_delegatesToHandleCallback() {
        Map<String, String> params = buildCallbackParams("success");
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.empty());

        paymentService.handleWebhook(params);

        verify(transactionRepository, atLeastOnce()).save(any(PaymentTransaction.class));
    }

    // ── Status query tests ────────────────────────────────────────────────────

    // Guarantees: getPaymentStatus returns a map with txnid, status, amount, and hashVerified for a known txnid
    @Test
    void getPaymentStatus_returnsCorrectFields_whenFound() {
        PaymentTransaction txn = new PaymentTransaction();
        txn.setTxnid("TXN-ABC");
        txn.setStatus("SUCCESS");
        txn.setAmount(999.0);
        txn.setHashVerified(true);
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(txn));

        Map<String, Object> result = paymentService.getPaymentStatus("TXN-ABC");

        assertThat(result).containsEntry("txnid", "TXN-ABC");
        assertThat(result).containsEntry("status", "SUCCESS");
        assertThat(result).containsEntry("amount", 999.0);
        assertThat(result).containsEntry("hashVerified", true);
    }

    // Guarantees: getPaymentStatus throws RuntimeException when txnid is not found in the DB
    @Test
    void getPaymentStatus_throwsException_whenNotFound() {
        when(transactionRepository.findByTxnid("MISSING")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> paymentService.getPaymentStatus("MISSING"));
    }

    // Guarantees: getStatusByResponseId returns found=true and the transaction status when a match exists
    @Test
    void getStatusByResponseId_returnsFoundTrue_whenTransactionExists() {
        PaymentTransaction txn = new PaymentTransaction();
        txn.setTxnid("TXN-ABC");
        txn.setStatus("SUCCESS");
        txn.setAmount(999.0);
        txn.setHashVerified(true);
        when(transactionRepository.findTopByFormResponseIdOrderByInitiatedAtDesc(42L))
                .thenReturn(Optional.of(txn));

        Map<String, Object> result = paymentService.getStatusByResponseId(42L);

        assertThat(result).containsEntry("found", true);
        assertThat(result).containsEntry("status", "SUCCESS");
    }

    // Guarantees: getStatusByResponseId returns found=false and status=NOT_INITIATED when no transaction exists for the responseId
    @Test
    void getStatusByResponseId_returnsNotInitiated_whenNoTransaction() {
        when(transactionRepository.findTopByFormResponseIdOrderByInitiatedAtDesc(99L))
                .thenReturn(Optional.empty());

        Map<String, Object> result = paymentService.getStatusByResponseId(99L);

        assertThat(result).containsEntry("found", false);
        assertThat(result).containsEntry("status", "NOT_INITIATED");
    }

    // Guarantees: getStatusByResponseId includes mihpayid in the result when the transaction has a non-null mihpayid
    @Test
    void getStatusByResponseId_includesMihpayid_whenTransactionHasMihpayid() {
        PaymentTransaction txn = new PaymentTransaction();
        txn.setTxnid("TXN-DEF");
        txn.setStatus("SUCCESS");
        txn.setAmount(500.0);
        txn.setMihpayid("MIHPAY999");
        when(transactionRepository.findTopByFormResponseIdOrderByInitiatedAtDesc(55L))
                .thenReturn(Optional.of(txn));

        Map<String, Object> result = paymentService.getStatusByResponseId(55L);

        assertThat(result).containsEntry("found", true);
        assertThat(result).containsEntry("mihpayid", "MIHPAY999");
    }
}
