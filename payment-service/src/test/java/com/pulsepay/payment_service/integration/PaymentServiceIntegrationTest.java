package com.pulsepay.payment_service.integration;

import com.pulsepay.payment_service.client.WalletClient;
import com.pulsepay.payment_service.dto.request.CreatePaymentRequest;
import com.pulsepay.payment_service.dto.response.CreateTransferResponse;
import com.pulsepay.payment_service.entity.Payment;
import com.pulsepay.payment_service.enums.PaymentStatus;
import com.pulsepay.payment_service.exception.WalletBusinessException;
import com.pulsepay.payment_service.exception.WalletCommunicationException;
import com.pulsepay.payment_service.repository.PaymentRepository;
import com.pulsepay.payment_service.security.token.CurrentAccessToken;
import com.pulsepay.payment_service.service.PaymentRecoveryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@WithMockUser
class PaymentServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private WalletClient walletClient;

    @Autowired
    private PaymentRecoveryService paymentRecoveryService;

    @MockitoBean
    private CurrentAccessToken currentAccessToken;
    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        paymentRepository.deleteAll();
    }
    @Test
    void shouldCreatePaymentSuccessfully() throws Exception {
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();

        BigDecimal amount = new BigDecimal("50.00");
        String currency = "USD";
        String idempotencyKey = "payment-key-001";

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        sourceWalletId,
                        destinationWalletId,
                        amount,
                        currency
                );

        CreateTransferResponse walletResponse =
                mock(CreateTransferResponse.class);

        when(walletResponse.status())
                .thenReturn("COMPLETED");

        when(currentAccessToken.get())
                .thenReturn("test-access-token");

        when(walletClient.executeTransfer(
                eq(sourceWalletId),
                eq(destinationWalletId),
                eq(amount),
                eq(currency),
                eq(idempotencyKey),
                eq("test-access-token")
        )).thenReturn(walletResponse);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/payments")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceWalletId").value(sourceWalletId.toString()))
                .andExpect(jsonPath("$.destinationWalletId").value(destinationWalletId.toString()))
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        List<Payment> payments = paymentRepository.findAll();

        assertThat(payments).hasSize(1);

        Payment payment = payments.get(0);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getSourceWalletId()).isEqualTo(sourceWalletId);
        assertThat(payment.getDestinationWalletId()).isEqualTo(destinationWalletId);
        assertThat(payment.getAmount()).isEqualByComparingTo(amount);
        assertThat(payment.getCurrency()).isEqualTo(currency);
        assertThat(payment.getIdempotencyKey()).isEqualTo(idempotencyKey);
    }
    @Test
    void shouldReturnExistingPaymentForSameIdempotencyKey() throws Exception {
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();

        BigDecimal amount = new BigDecimal("50.00");
        String currency = "USD";
        String idempotencyKey = "payment-key-002";

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        sourceWalletId,
                        destinationWalletId,
                        amount,
                        currency
                );

        CreateTransferResponse walletResponse =
                mock(CreateTransferResponse.class);

        when(walletResponse.status())
                .thenReturn("COMPLETED");

        when(currentAccessToken.get())
                .thenReturn("test-access-token");

        when(walletClient.executeTransfer(
                eq(sourceWalletId),
                eq(destinationWalletId),
                eq(amount),
                eq(currency),
                eq(idempotencyKey),
                eq("test-access-token")
        )).thenReturn(walletResponse);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/payments")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/payments")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        List<Payment> payments = paymentRepository.findAll();

        assertThat(payments).hasSize(1);

        verify(walletClient, times(1)).executeTransfer(
                eq(sourceWalletId),
                eq(destinationWalletId),
                eq(amount),
                eq(currency),
                eq(idempotencyKey),
                eq("test-access-token")
        );
    }
    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentRequest() throws Exception {
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();

        String idempotencyKey = "payment-key-003";

        CreatePaymentRequest firstRequest =
                new CreatePaymentRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("50.00"),
                        "USD"
                );

        CreatePaymentRequest secondRequest =
                new CreatePaymentRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("100.00"),
                        "USD"
                );

        CreateTransferResponse walletResponse =
                mock(CreateTransferResponse.class);

        when(walletResponse.status())
                .thenReturn("COMPLETED");

        when(currentAccessToken.get())
                .thenReturn("test-access-token");

        when(walletClient.executeTransfer(
                eq(sourceWalletId),
                eq(destinationWalletId),
                eq(new BigDecimal("50.00")),
                eq("USD"),
                eq(idempotencyKey),
                eq("test-access-token")
        )).thenReturn(walletResponse);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/payments")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(firstRequest))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/payments")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(secondRequest))
                )
                .andExpect(status().isConflict());

        List<Payment> payments = paymentRepository.findAll();

        assertThat(payments).hasSize(1);

        assertThat(payments.get(0).getAmount())
                .isEqualByComparingTo("50.00");

        verify(walletClient, times(1)).executeTransfer(
                eq(sourceWalletId),
                eq(destinationWalletId),
                eq(new BigDecimal("50.00")),
                eq("USD"),
                eq(idempotencyKey),
                eq("test-access-token")
        );
    }
    @Test
    void shouldMarkPaymentFailedWhenWalletRejectsTransfer() throws Exception {
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();

        BigDecimal amount = new BigDecimal("50.00");
        String currency = "USD";
        String idempotencyKey = "payment-key-004";

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        sourceWalletId,
                        destinationWalletId,
                        amount,
                        currency
                );

        when(currentAccessToken.get())
                .thenReturn("test-access-token");

        when(walletClient.executeTransfer(
                eq(sourceWalletId),
                eq(destinationWalletId),
                eq(amount),
                eq(currency),
                eq(idempotencyKey),
                eq("test-access-token")
        )).thenThrow(
                new WalletBusinessException("Wallet rejected the transfer")
        );

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/payments")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        List<Payment> payments = paymentRepository.findAll();

        assertThat(payments).hasSize(1);

        Payment payment = payments.get(0);

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        assertThat(payment.getIdempotencyKey())
                .isEqualTo(idempotencyKey);
    }
    @Test
    void shouldKeepPaymentProcessingWhenWalletCommunicationFails() throws Exception {
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();

        BigDecimal amount = new BigDecimal("50.00");
        String currency = "USD";
        String idempotencyKey = "payment-key-005";

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        sourceWalletId,
                        destinationWalletId,
                        amount,
                        currency
                );

        when(currentAccessToken.get())
                .thenReturn("test-access-token");

        when(walletClient.executeTransfer(
                eq(sourceWalletId),
                eq(destinationWalletId),
                eq(amount),
                eq(currency),
                eq(idempotencyKey),
                eq("test-access-token")
        )).thenThrow(
                new WalletCommunicationException(
                        "Unable to obtain reliable response from Wallet Service",
                        new RuntimeException("Connection failed")
                )
        );

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/payments")
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        List<Payment> payments = paymentRepository.findAll();

        assertThat(payments).hasSize(1);

        Payment payment = payments.get(0);

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.PROCESSING);

        assertThat(payment.getIdempotencyKey())
                .isEqualTo(idempotencyKey);
    }
    @Test
    void shouldGetExistingPayment() throws Exception {
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();

        Payment payment = new Payment();

        payment.setSourceWalletId(sourceWalletId);
        payment.setDestinationWalletId(destinationWalletId);
        payment.setAmount(new BigDecimal("75.00"));
        payment.setCurrency("USD");
        payment.setIdempotencyKey("payment-key-006");
        payment.setRequestFingerprint(
                sourceWalletId
                        + "|"
                        + destinationWalletId
                        + "|"
                        + new BigDecimal("75.00")
                        + "|USD"
        );
        payment.markProcessing();
        payment.markCompleted();

        Payment savedPayment = paymentRepository.save(payment);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/payments/" + savedPayment.getId())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedPayment.getId().toString()))
                .andExpect(jsonPath("$.sourceWalletId").value(sourceWalletId.toString()))
                .andExpect(jsonPath("$.destinationWalletId").value(destinationWalletId.toString()))
                .andExpect(jsonPath("$.amount").value(75.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
    @Test
    void shouldReturnNotFoundWhenPaymentDoesNotExist() throws Exception {
        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/payments/" + paymentId)
                )
                .andExpect(status().isNotFound());
    }
    @Test
    void shouldRecoverStaleProcessingPayment() throws Exception {
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("80.0000");
        String currency = "USD";
        String idempotencyKey = "payment-key-008";
        Payment payment = new Payment();
        payment.setSourceWalletId(sourceWalletId);
        payment.setDestinationWalletId(destinationWalletId);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setRequestFingerprint(
                sourceWalletId
                        + "|"
                        + destinationWalletId
                        + "|"
                        + amount
                        + "|"
                        + currency
        );
        payment.markProcessing();
        Payment savedPayment = paymentRepository.save(payment);
        jdbcTemplate.update(
                "UPDATE payments SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(120)),
                savedPayment.getId());
        CreateTransferResponse walletResponse = mock(CreateTransferResponse.class);
        when(walletResponse.status())
                .thenReturn("COMPLETED");
        when(walletClient.executeInternalTransfer(
                eq(sourceWalletId),
                eq(destinationWalletId),
                eq(amount),
                eq(currency),
                eq(idempotencyKey)
                )).thenReturn(walletResponse);
        paymentRecoveryService.recoverStalePayments();
        Payment recoveredPayment =
                paymentRepository.findById(savedPayment.getId())
                        .orElseThrow();
        assertThat(recoveredPayment.getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        verify(walletClient, times(1)).executeInternalTransfer(
                eq(sourceWalletId),
                eq(destinationWalletId),
                eq(amount),
                eq(currency),
                eq(idempotencyKey)
        );








    }


}
