package com.cyberlearnix.form.service;

import com.cyberlearnix.form.util.HashTestUtil;
import com.cyberlearnix.shared.entity.enrollment.PaymentTransaction;
import com.cyberlearnix.shared.entity.form.GeneralForm;
import com.cyberlearnix.shared.entity.form.GeneralFormResponse;
import com.cyberlearnix.shared.repository.enrollment.PaymentTransactionRepository;
import com.cyberlearnix.shared.repository.form.GeneralFormRepository;
import com.cyberlearnix.shared.repository.form.GeneralFormResponseRepository;
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
 * Unit tests for {@link FormPaymentService} covering initiate, callback, webhook-via-self,
 * payment-status queries, and form payment info.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FormPaymentServiceTest {

    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private GeneralFormResponseRepository responseRepository;
    @Mock private GeneralFormRepository formRepository;

    @InjectMocks
    private FormPaymentService formPaymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(formPaymentService, "merchantKey",  "fkey");
        ReflectionTestUtils.setField(formPaymentService, "merchantSalt", "fsalt");
        ReflectionTestUtils.setField(formPaymentService, "payuBaseUrl",  "https://secure.payu.in");
        ReflectionTestUtils.setField(formPaymentService, "frontendUrl",  "http://localhost:5173");
        // Inject self as the real instance so handleWebhook → self.handleCallback exercises real code
        ReflectionTestUtils.setField(formPaymentService, "self", formPaymentService);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private GeneralForm buildForm(String id, boolean paymentEnabled, Double totalAmount) {
        GeneralForm form = new GeneralForm();
        form.setId(id);
        form.setTitle("Test Form");
        form.setPaymentEnabled(paymentEnabled);
        form.setTotalAmount(totalAmount);
        form.setToken("tok123");
        return form;
    }

    private GeneralFormResponse buildResponse(Long id, String formId) {
        GeneralFormResponse r = new GeneralFormResponse();
        r.setId(id);
        r.setFormId(formId);
        r.setPaymentStatus("PENDING");
        return r;
    }

    /**
     * Builds callback params whose hash is the CORRECT reverse hash for the given status.
     * Reverse hash format: salt|status|||||||||||email|firstname|productinfo|amount|txnid|key
     */
    private Map<String, String> buildCallbackParams(String status, String txnid) {
        String reverseHashInput = "fsalt|" + status
                + "|||||||||||alice@test.com|Alice|Test Form|999.00|" + txnid + "|fkey";
        String hash = HashTestUtil.sha512(reverseHashInput);
        Map<String, String> params = new HashMap<>();
        params.put("status",      status);
        params.put("txnid",       txnid);
        params.put("mihpayid",    "MIHPAY456");
        params.put("amount",      "999.00");
        params.put("productinfo", "Test Form");
        params.put("firstname",   "Alice");
        params.put("email",       "alice@test.com");
        params.put("hash",        hash);
        return params;
    }

    // ── initiatePayment ───────────────────────────────────────────────────────

    // Guarantees: initiatePayment returns a map with success=true, a FTXN-prefixed txnid, and a paymentData sub-map
    @Test
    void initiatePayment_returnsSuccessMap_withTxnidAndPaymentData() {
        GeneralFormResponse response = buildResponse(1L, "form-1");
        GeneralForm form = buildForm("form-1", true, 999.0);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(formRepository.findById("form-1")).thenReturn(Optional.of(form));

        Map<String, Object> result = formPaymentService.initiatePayment(
                1L, "Alice", "alice@test.com", "9876543210");

        assertThat(result.get("success")).isEqualTo(true);
        assertThat((String) result.get("txnid")).startsWith("FTXN");

        @SuppressWarnings("unchecked")
        Map<String, String> paymentData = (Map<String, String>) result.get("paymentData");
        assertThat(paymentData).containsKey("key");
        assertThat(paymentData).containsKey("hash");
        assertThat(paymentData).containsKey("action");
    }

    // Guarantees: initiatePayment throws RuntimeException when the form response does not exist
    @Test
    void initiatePayment_throwsException_whenFormResponseNotFound() {
        when(responseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> formPaymentService.initiatePayment(99L, "Alice", "alice@test.com", null));
    }

    // Guarantees: initiatePayment throws RuntimeException when the form has payment disabled
    @Test
    void initiatePayment_throwsException_whenPaymentNotEnabled() {
        GeneralFormResponse response = buildResponse(1L, "form-free");
        GeneralForm form = buildForm("form-free", false, null);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(formRepository.findById("form-free")).thenReturn(Optional.of(form));

        assertThrows(RuntimeException.class,
                () -> formPaymentService.initiatePayment(1L, "Alice", "alice@test.com", null));
    }

    // Guarantees: initiatePayment throws RuntimeException when totalAmount is zero (no valid amount to charge)
    @Test
    void initiatePayment_throwsException_whenTotalAmountIsZero() {
        GeneralFormResponse response = buildResponse(1L, "form-zero");
        GeneralForm form = buildForm("form-zero", true, 0.0);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(formRepository.findById("form-zero")).thenReturn(Optional.of(form));

        assertThrows(RuntimeException.class,
                () -> formPaymentService.initiatePayment(1L, "Alice", "alice@test.com", null));
    }

    // Guarantees: txnid starts with "FTXN" followed by all digits (timestamp-based)
    // GAP: FormPaymentService uses System.currentTimeMillis() for txnid — collision risk under concurrent load.
    // See BUG-002 for the enrollment-service fix (UUID-based). FormPaymentService has not been migrated.
    @Test
    void initiatePayment_txnid_usesTimestampFormat_notUuid() {
        GeneralFormResponse response = buildResponse(1L, "form-ts");
        GeneralForm form = buildForm("form-ts", true, 999.0);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(formRepository.findById("form-ts")).thenReturn(Optional.of(form));

        Map<String, Object> result = formPaymentService.initiatePayment(
                1L, "Alice", "alice@test.com", "9876543210");

        String txnid = (String) result.get("txnid");
        assertThat(txnid).startsWith("FTXN");
        // Remaining characters should be digits (timestamp millis)
        assertThat(txnid.substring(4)).matches("\\d+");
    }

    // ── handleCallback ────────────────────────────────────────────────────────

    // Guarantees: when the reverse hash matches for status="success", result.status is SUCCESS and hashVerified is true
    @Test
    void handleCallback_setsStatusSUCCESS_whenHashIsValid() {
        Map<String, String> params = buildCallbackParams("success", "FTXN-TEST-001");
        PaymentTransaction txn = new PaymentTransaction();
        txn.setTxnid("FTXN-TEST-001");
        txn.setStatus("PENDING");
        // formResponseId=null avoids touching responseRepository
        when(transactionRepository.findByTxnid("FTXN-TEST-001")).thenReturn(Optional.of(txn));

        Map<String, Object> result = formPaymentService.handleCallback(params);

        assertThat(result.get("status")).isEqualTo("SUCCESS");
        assertThat(result.get("hashVerified")).isEqualTo(true);
    }

    // Guarantees: when the hash does not match, result.status is FAILURE and hashVerified is false even if status param is "success"
    @Test
    void handleCallback_setsStatusFAILURE_whenHashMismatch() {
        Map<String, String> params = buildCallbackParams("success", "FTXN-TEST-002");
        params.put("hash", "BADHASH");
        PaymentTransaction txn = new PaymentTransaction();
        txn.setTxnid("FTXN-TEST-002");
        txn.setStatus("PENDING");
        when(transactionRepository.findByTxnid("FTXN-TEST-002")).thenReturn(Optional.of(txn));

        Map<String, Object> result = formPaymentService.handleCallback(params);

        assertThat(result.get("status")).isEqualTo("FAILURE");
        assertThat(result.get("hashVerified")).isEqualTo(false);
    }

    // Guarantees: when txnid is not found in DB, a new PaymentTransaction is created and saved with txnid from params
    @Test
    void handleCallback_createsNewTransaction_whenTxnidNotFound() {
        Map<String, String> params = buildCallbackParams("success", "FTXN-NEW-001");
        when(transactionRepository.findByTxnid("FTXN-NEW-001")).thenReturn(Optional.empty());

        formPaymentService.handleCallback(params);

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTxnid()).isEqualTo("FTXN-NEW-001");
    }

    // Guarantees: handleWebhook calls handleCallback via the self proxy, and the transaction is persisted
    @Test
    void handleWebhook_delegatesToHandleCallbackViaSelf() {
        // self = real formPaymentService instance; handleWebhook → self.handleCallback runs real code
        Map<String, String> params = buildCallbackParams("success", "FTXN-WH-001");
        when(transactionRepository.findByTxnid("FTXN-WH-001")).thenReturn(Optional.empty());

        formPaymentService.handleWebhook(params);

        verify(transactionRepository, atLeastOnce()).save(any(PaymentTransaction.class));
    }

    // ── getFormPaymentInfo ────────────────────────────────────────────────────

    // Guarantees: getFormPaymentInfo returns all payment fields from the form entity
    @Test
    void getFormPaymentInfo_returnsFormFields_whenFound() {
        GeneralForm form = buildForm("form-info", true, 999.0);
        form.setPaymentAmount(847.46);
        form.setGstPercent(18);
        form.setGstAmount(179.82);
        when(formRepository.findById("form-info")).thenReturn(Optional.of(form));

        Map<String, Object> result = formPaymentService.getFormPaymentInfo("form-info");

        assertThat(result.get("paymentEnabled")).isEqualTo(true);
        assertThat(result.get("totalAmount")).isEqualTo(999.0);
        assertThat(result.get("gstPercent")).isEqualTo(18);
        assertThat(result.get("gstAmount")).isEqualTo(179.82);
    }

    // Guarantees: getFormPaymentInfo throws RuntimeException when the form does not exist
    @Test
    void getFormPaymentInfo_throwsException_whenFormNotFound() {
        when(formRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> formPaymentService.getFormPaymentInfo("ghost"));
    }

    // ── getPaymentStatus ──────────────────────────────────────────────────────

    // Guarantees: getPaymentStatus returns all student info fields and the linked submission data
    @Test
    void getPaymentStatus_returnsAllFields_includingStudentInfo() {
        PaymentTransaction txn = new PaymentTransaction();
        txn.setTxnid("FTXN-STATUS-001");
        txn.setStatus("SUCCESS");
        txn.setStudentName("Alice");
        txn.setStudentEmail("alice@test.com");
        txn.setStudentPhone("9876543210");
        txn.setAmount(999.0);
        txn.setFormResponseId(20L);
        when(transactionRepository.findByTxnid("FTXN-STATUS-001")).thenReturn(Optional.of(txn));

        GeneralFormResponse response = buildResponse(20L, "form-1");
        response.setSubmissionData("{\"name\":\"Alice\"}");
        response.setPaymentStatus("PAID");
        response.setAmountPaid(999.0);
        when(responseRepository.findById(20L)).thenReturn(Optional.of(response));

        Map<String, Object> result = formPaymentService.getPaymentStatus("FTXN-STATUS-001");

        assertThat(result.get("studentName")).isEqualTo("Alice");
        assertThat(result.get("studentEmail")).isEqualTo("alice@test.com");
        assertThat(result.get("submissionData")).isEqualTo("{\"name\":\"Alice\"}");
        assertThat(result.get("paymentStatus")).isEqualTo("PAID");
    }

    // Guarantees: getPaymentStatus throws RuntimeException when the txnid is not found
    @Test
    void getPaymentStatus_throwsException_whenNotFound() {
        when(transactionRepository.findByTxnid("GHOST")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> formPaymentService.getPaymentStatus("GHOST"));
    }
}
