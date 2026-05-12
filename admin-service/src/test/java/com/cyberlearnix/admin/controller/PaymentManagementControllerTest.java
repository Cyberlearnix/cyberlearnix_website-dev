package com.cyberlearnix.admin.controller;

import com.cyberlearnix.admin.client.EnrollmentServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link PaymentManagementController} covering order listing,
 * order details, status update, and refund endpoints via standalone MockMvc.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PaymentManagementControllerTest {

    @Mock
    private EnrollmentServiceClient enrollmentServiceClient;

    private PaymentManagementController controller;
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        // @RequiredArgsConstructor → constructor injection; instantiate manually so Mockito mock is wired
        controller = new PaymentManagementController(enrollmentServiceClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // ── GET /api/admin/orders ─────────────────────────────────────────────────

    // Guarantees: GET /api/admin/orders returns 200 with the full list of orders from the enrollment service
    @Test
    void getAllOrders_returns200_withOrderList() throws Exception {
        List<Map<String, Object>> orders = List.of(
                Map.of("id", 1, "status", "SUCCESS"),
                Map.of("id", 2, "status", "PENDING")
        );
        when(enrollmentServiceClient.getAllOrders(isNull(), any())).thenReturn(orders);

        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // Guarantees: when a status query param is supplied, it is forwarded verbatim to the enrollment service client
    @Test
    @SuppressWarnings("unchecked")
    void getAllOrders_withStatusFilter_passesStatusToClient() throws Exception {
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        when(enrollmentServiceClient.getAllOrders(statusCaptor.capture(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "PENDING")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());

        assertThat(statusCaptor.getValue()).isEqualTo("PENDING");
    }

    // ── GET /api/admin/orders/{id} ────────────────────────────────────────────

    // Guarantees: GET /api/admin/orders/{id} returns 200 with order details when the enrollment service responds normally
    @Test
    void getOrderDetails_returns200_whenFound() throws Exception {
        when(enrollmentServiceClient.getOrderById(eq(42L), any()))
                .thenReturn(Map.of("id", 42, "status", "SUCCESS"));

        mockMvc.perform(get("/api/admin/orders/42")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    // Guarantees: GET /api/admin/orders/{id} returns 404 when the enrollment service throws (order not found)
    @Test
    void getOrderDetails_returns404_whenClientThrows() throws Exception {
        when(enrollmentServiceClient.getOrderById(eq(42L), any()))
                .thenThrow(new RuntimeException("order not found"));

        mockMvc.perform(get("/api/admin/orders/42")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/admin/orders/{id}/status ─────────────────────────────────────

    // Guarantees: a valid status update request returns 200 with the updated order map
    @Test
    void updateOrderStatus_returns200_withValidStatus() throws Exception {
        when(enrollmentServiceClient.updateOrderStatus(eq(42L), any(), any()))
                .thenReturn(Map.of("id", 42, "status", "REFUNDED"));

        mockMvc.perform(put("/api/admin/orders/42/status")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "REFUNDED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    // Guarantees: when the request body omits the "status" field, the endpoint returns 400 without calling the client
    @Test
    void updateOrderStatus_returns400_whenStatusFieldIsMissing() throws Exception {
        mockMvc.perform(put("/api/admin/orders/42/status")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ── POST /api/admin/orders/{id}/refund ────────────────────────────────────

    // Guarantees: POST /api/admin/orders/{id}/refund returns 200 with the refund result from the enrollment service
    @Test
    void processRefund_returns200() throws Exception {
        when(enrollmentServiceClient.processRefund(eq(42L), any()))
                .thenReturn(Map.of("refunded", true));

        mockMvc.perform(post("/api/admin/orders/42/refund")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunded").value(true));
    }
}
