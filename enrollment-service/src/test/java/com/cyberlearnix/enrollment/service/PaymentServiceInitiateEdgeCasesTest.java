package com.cyberlearnix.enrollment.service;

import com.cyberlearnix.enrollment.util.HashTestUtil;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentService#initiatePayment} covering edge cases:
 * disabled payment, missing config, pipe sanitization, coupon/discount, and phone normalisation.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PaymentServiceInitiateEdgeCasesTest {

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
    }

    private EnrollmentFormResponse buildResponse(String formId) {
        EnrollmentFormResponse r = new EnrollmentFormResponse();
        r.setId(1L);
        r.setFormId(formId);
        return r;
    }

    private EnrollmentFormConfig buildConfig(String id, String title, boolean paymentEnabled, Double amount) {
        EnrollmentFormConfig c = new EnrollmentFormConfig();
        c.setId(id);
        c.setTitle(title);
        c.setPaymentEnabled(paymentEnabled);
        c.setPaymentAmount(amount);
        return c;
    }

    // Guarantees: initiatePayment throws RuntimeException when the form response ID does not exist
    @Test
    void initiatePayment_throwsException_whenFormResponseNotFound() {
        when(responseRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> paymentService.initiatePayment(99L, "Alice", "alice@test.com", null, null));

        assertThat(ex.getMessage()).contains("Form response not found");
    }

    // Guarantees: initiatePayment throws RuntimeException when the form config (by formId) does not exist
    @Test
    void initiatePayment_throwsException_whenFormNotFound() {
        EnrollmentFormResponse response = buildResponse("form-missing");
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-missing")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> paymentService.initiatePayment(1L, "Alice", "alice@test.com", null, null));
    }

    // Guarantees: initiatePayment throws RuntimeException when the form config has payment disabled
    @Test
    void initiatePayment_throwsException_whenPaymentNotEnabled() {
        EnrollmentFormResponse response = buildResponse("form-nopay");
        EnrollmentFormConfig config = buildConfig("form-nopay", "Free Course", false, null);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-nopay")).thenReturn(Optional.of(config));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> paymentService.initiatePayment(1L, "Alice", "alice@test.com", null, null));

        assertThat(ex.getMessage()).contains("payment");
    }

    // Guarantees: initiatePayment throws RuntimeException when paymentAmount is null and courseServiceClient also fails
    @Test
    void initiatePayment_throwsException_whenAmountNotConfigured() {
        EnrollmentFormResponse response = buildResponse("form-noamt");
        EnrollmentFormConfig config = buildConfig("form-noamt", "No Amount Course", true, null);
        config.setCourseId(99L);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-noamt")).thenReturn(Optional.of(config));
        when(courseServiceClient.getCoursePrice(99L)).thenThrow(new RuntimeException("feign error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> paymentService.initiatePayment(1L, "Alice", "alice@test.com", null, null));

        assertThat(ex.getMessage()).contains("Payment amount is not configured");
    }

    // Guarantees: pipe characters in productInfo and firstname are stripped before hash computation, preventing hash corruption
    @Test
    void initiatePayment_stripsHashUnsafeChars_fromProductInfoAndFirstname() {
        EnrollmentFormResponse response = buildResponse("form-pipe");
        EnrollmentFormConfig config = buildConfig("form-pipe", "Course|With|Pipes", true, 999.0);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-pipe")).thenReturn(Optional.of(config));

        Map<String, Object> result = paymentService.initiatePayment(
                1L, "John|Doe", "john@test.com", "9876543210", null);

        @SuppressWarnings("unchecked")
        Map<String, String> paymentData = (Map<String, String>) result.get("paymentData");
        String txnid  = paymentData.get("txnid");
        String amount = paymentData.get("amount");

        // Independently compute expected hash with sanitized strings (pipes replaced by spaces)
        String expectedHashInput = "test-key|" + txnid + "|" + amount
                + "|Course With Pipes|John Doe|john@test.com|||||||||||test-salt";
        String expectedHash = HashTestUtil.sha512(expectedHashInput);

        assertThat(paymentData.get("hash")).isEqualTo(expectedHash);
        assertThat(paymentData.get("productinfo")).isEqualTo("Course With Pipes");
    }

    // Guarantees: coupon discount is subtracted from the payment amount before hashing; final amount is (original - discount)
    @Test
    void initiatePayment_appliesCouponDiscount_reducesAmount() {
        EnrollmentFormResponse response = buildResponse("form-1");
        EnrollmentFormConfig config = buildConfig("form-1", "Test Course", true, 1000.0);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-1")).thenReturn(Optional.of(config));
        when(couponService.applyAndConsume("DISC20", 1000.0)).thenReturn(200.0);

        Map<String, Object> result = paymentService.initiatePayment(
                1L, "Alice", "alice@test.com", "9876543210", "DISC20");

        @SuppressWarnings("unchecked")
        Map<String, String> paymentData = (Map<String, String>) result.get("paymentData");
        assertThat(paymentData.get("amount")).isEqualTo("800.00");
    }

    // Guarantees: form-level PERCENTAGE discount reduces the amount by the configured percentage when no coupon is supplied
    @Test
    void initiatePayment_appliesFormPercentageDiscount_whenCouponIsBlank() {
        EnrollmentFormResponse response = buildResponse("form-pct");
        EnrollmentFormConfig config = buildConfig("form-pct", "Test Course", true, 1000.0);
        config.setDiscountEnabled(true);
        config.setDiscountType("PERCENTAGE");
        config.setDiscountValue(10.0); // 10% off
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-pct")).thenReturn(Optional.of(config));

        Map<String, Object> result = paymentService.initiatePayment(
                1L, "Alice", "alice@test.com", "9876543210", null);

        @SuppressWarnings("unchecked")
        Map<String, String> paymentData = (Map<String, String>) result.get("paymentData");
        assertThat(paymentData.get("amount")).isEqualTo("900.00");
    }

    // Guarantees: form-level FLAT discount subtracts a fixed amount from the price when no coupon is supplied
    @Test
    void initiatePayment_appliesFormFlatDiscount_whenCouponIsBlank() {
        EnrollmentFormResponse response = buildResponse("form-flat");
        EnrollmentFormConfig config = buildConfig("form-flat", "Test Course", true, 500.0);
        config.setDiscountEnabled(true);
        config.setDiscountType("FLAT");
        config.setDiscountValue(150.0);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-flat")).thenReturn(Optional.of(config));

        Map<String, Object> result = paymentService.initiatePayment(
                1L, "Alice", "alice@test.com", "9876543210", null);

        @SuppressWarnings("unchecked")
        Map<String, String> paymentData = (Map<String, String>) result.get("paymentData");
        assertThat(paymentData.get("amount")).isEqualTo("350.00");
    }

    // Guarantees: a phone number longer than 10 digits is truncated to the first 10 digits in the PayU payload
    @Test
    void initiatePayment_phoneNormalized_toTenDigits_whenValid() {
        EnrollmentFormResponse response = buildResponse("form-phone");
        EnrollmentFormConfig config = buildConfig("form-phone", "Test Course", true, 999.0);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-phone")).thenReturn(Optional.of(config));

        Map<String, Object> result = paymentService.initiatePayment(
                1L, "Alice", "alice@test.com", "98765432109", null); // 11 digits

        @SuppressWarnings("unchecked")
        Map<String, String> paymentData = (Map<String, String>) result.get("paymentData");
        assertThat(paymentData.get("phone")).isEqualTo("9876543210");
    }

    // Guarantees: a null phone falls back to "0000000000" so the PayU form always has a valid phone field
    @Test
    void initiatePayment_phoneFallback_whenPhoneIsNull() {
        EnrollmentFormResponse response = buildResponse("form-phone2");
        EnrollmentFormConfig config = buildConfig("form-phone2", "Test Course", true, 999.0);
        when(responseRepository.findById(1L)).thenReturn(Optional.of(response));
        when(configRepository.findById("form-phone2")).thenReturn(Optional.of(config));

        Map<String, Object> result = paymentService.initiatePayment(
                1L, "Alice", "alice@test.com", null, null);

        @SuppressWarnings("unchecked")
        Map<String, String> paymentData = (Map<String, String>) result.get("paymentData");
        assertThat(paymentData.get("phone")).isEqualTo("0000000000");
    }
}
