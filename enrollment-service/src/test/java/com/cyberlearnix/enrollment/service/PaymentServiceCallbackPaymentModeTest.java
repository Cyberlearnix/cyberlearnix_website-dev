package com.cyberlearnix.enrollment.service;

import com.cyberlearnix.enrollment.util.HashTestUtil;
import com.cyberlearnix.enrollment.client.CourseServiceClient;
import com.cyberlearnix.shared.entity.enrollment.EnrollmentFormResponse;
import com.cyberlearnix.shared.entity.enrollment.PaymentTransaction;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormConfigRepository;
import com.cyberlearnix.shared.repository.enrollment.EnrollmentFormResponseRepository;
import com.cyberlearnix.shared.repository.enrollment.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PaymentService.handleCallback() covering:
 *  - transactionId persistence on success (already in codebase)
 *  - paymentMode mapping from PayU mode codes (TODO: Shiva's fix)
 *  - security: transactionId must NOT be set when hash verification fails
 *  - edge case: null formResponseId on the transaction
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PaymentServiceCallbackPaymentModeTest {

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
        ReflectionTestUtils.setField(paymentService, "backendUrl",  "http://localhost:8083");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a valid success callback params map with a correct reverse hash.
     * The optional {@code mode} param simulates the PayU payment mode (UPI, CC, NB).
     */
    private Map<String, String> buildCallbackParams(String status, String mode) {
        String reverseHashInput = "test-salt|" + status
                + "|||||||alice@test.com|Alice|Test Course|999.00|TXN-ABC|test-key";
        String hash = HashTestUtil.sha512(reverseHashInput);
        Map<String, String> params = new HashMap<>();
        params.put("status",      status);
        params.put("txnid",       "TXN-ABC");
        params.put("mihpayid",    "MIHPAY123");
        params.put("amount",      "999.00");
        params.put("productinfo", "Test Course");
        params.put("firstname",   "Alice");
        params.put("email",       "alice@test.com");
        params.put("udf1",        "");
        params.put("hash",        hash);
        params.put("mode",        mode);
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
        r.setAmountPaid(0.0);
        return r;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    // Guarantees: after handleCallback with status=success and a valid hash,
    // the saved EnrollmentFormResponse has a non-null transactionId equal to the txnid.
    // This PASSES now — handleCallback already sets transactionId on the response record.
    @Test
    void handleCallback_setsTransactionIdOnResponse_whenPaymentSuccess() {
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(buildPendingTxn()));
        when(responseRepository.findById(10L)).thenReturn(Optional.of(buildFormResponse()));

        paymentService.handleCallback(buildCallbackParams("success", "UPI"));

        ArgumentCaptor<EnrollmentFormResponse> captor = ArgumentCaptor.forClass(EnrollmentFormResponse.class);
        verify(responseRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionId())
                .as("transactionId must be set to the PayU txnid after successful callback")
                .isNotNull()
                .isEqualTo("TXN-ABC");
    }

    // Guarantees: paymentMode on the saved EnrollmentFormResponse is "UPI" when PayU sends mode=UPI.
    // TODO: Will pass once Shiva's fix is merged — paymentMode field and resolvePaymentMode()
    //       not yet added to EnrollmentFormResponse / PaymentService.handleCallback().
    @Disabled("paymentMode field not yet implemented - waiting for Shiva's fix")
    @Test
    void handleCallback_setsPaymentModeUPI_whenModeIsUPI() {
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(buildPendingTxn()));
        when(responseRepository.findById(10L)).thenReturn(Optional.of(buildFormResponse()));

        paymentService.handleCallback(buildCallbackParams("success", "UPI"));

        ArgumentCaptor<EnrollmentFormResponse> captor = ArgumentCaptor.forClass(EnrollmentFormResponse.class);
        verify(responseRepository).save(captor.capture());
        // Uses reflection so this compiles even before the field exists.
        // Throws IllegalArgumentException if "paymentMode" field is absent — that IS the expected failure.
        Object paymentMode = ReflectionTestUtils.getField(captor.getValue(), "paymentMode");
        assertThat(paymentMode)
                .as("paymentMode should be 'UPI' when PayU mode=UPI")
                .isEqualTo("UPI");
    }

    // Guarantees: paymentMode is "Credit Card" when PayU sends mode=CC.
    // TODO: Will pass once Shiva's fix is merged — resolvePaymentMode() and paymentMode field not yet added.
    @Disabled("paymentMode field not yet implemented - waiting for Shiva's fix")
    @Test
    void handleCallback_setsPaymentModeCreditCard_whenModeIsCC() {
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(buildPendingTxn()));
        when(responseRepository.findById(10L)).thenReturn(Optional.of(buildFormResponse()));

        paymentService.handleCallback(buildCallbackParams("success", "CC"));

        ArgumentCaptor<EnrollmentFormResponse> captor = ArgumentCaptor.forClass(EnrollmentFormResponse.class);
        verify(responseRepository).save(captor.capture());
        Object paymentMode = ReflectionTestUtils.getField(captor.getValue(), "paymentMode");
        assertThat(paymentMode)
                .as("paymentMode should be 'Credit Card' when PayU mode=CC")
                .isEqualTo("Credit Card");
    }

    // Guarantees: paymentMode is "Net Banking" when PayU sends mode=NB.
    // TODO: Will pass once Shiva's fix is merged — resolvePaymentMode() and paymentMode field not yet added.
    @Disabled("paymentMode field not yet implemented - waiting for Shiva's fix")
    @Test
    void handleCallback_setsPaymentModeNetBanking_whenModeIsNB() {
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(buildPendingTxn()));
        when(responseRepository.findById(10L)).thenReturn(Optional.of(buildFormResponse()));

        paymentService.handleCallback(buildCallbackParams("success", "NB"));

        ArgumentCaptor<EnrollmentFormResponse> captor = ArgumentCaptor.forClass(EnrollmentFormResponse.class);
        verify(responseRepository).save(captor.capture());
        Object paymentMode = ReflectionTestUtils.getField(captor.getValue(), "paymentMode");
        assertThat(paymentMode)
                .as("paymentMode should be 'Net Banking' when PayU mode=NB")
                .isEqualTo("Net Banking");
    }

    // Guarantees: when the reverse hash fails verification, transactionId must NOT be set on
    // the EnrollmentFormResponse — setting it on a tampered/failed callback is a security issue.
    // TODO: Will pass once Shiva's fix is merged — currently handleCallback sets transactionId
    //       unconditionally regardless of hash outcome (pre-existing bug this test documents).
    @Test
    void handleCallback_doesNotSetTransactionId_whenHashVerificationFails() {
        Map<String, String> params = buildCallbackParams("success", "UPI");
        params.put("hash", "tampered_totally_wrong_hash");
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(buildPendingTxn()));
        when(responseRepository.findById(10L)).thenReturn(Optional.of(buildFormResponse()));

        Map<String, Object> result = paymentService.handleCallback(params);

        assertThat(result).containsEntry("hashVerified", false);
        assertThat(result).containsEntry("status", "FAILURE");

        ArgumentCaptor<EnrollmentFormResponse> captor = ArgumentCaptor.forClass(EnrollmentFormResponse.class);
        verify(responseRepository).save(captor.capture());
        // Contract: transactionId MUST remain null when hash verification fails.
        // This fails today because the code sets it unconditionally — fixing that is Shiva's task.
        assertThat(captor.getValue().getTransactionId())
                .as("transactionId must not be persisted when the PayU hash does not verify")
                .isNull();
    }

    // Edge case: when the linked PaymentTransaction has a null formResponseId,
    // handleCallback must complete without exception and must NOT attempt to save any
    // EnrollmentFormResponse (no row to update).
    @Test
    void handleCallback_setsTransactionIdNull_whenResponseIdIsNull() {
        PaymentTransaction txnNullRef = new PaymentTransaction();
        txnNullRef.setTxnid("TXN-ABC");
        txnNullRef.setStatus("PENDING");
        txnNullRef.setFormResponseId(null);  // no linked form response
        when(transactionRepository.findByTxnid("TXN-ABC")).thenReturn(Optional.of(txnNullRef));

        // Must not throw
        paymentService.handleCallback(buildCallbackParams("success", "UPI"));

        // Transaction saved, but no response save should occur
        verify(transactionRepository).save(any(PaymentTransaction.class));
        verify(responseRepository, never()).save(any(EnrollmentFormResponse.class));
    }
}
