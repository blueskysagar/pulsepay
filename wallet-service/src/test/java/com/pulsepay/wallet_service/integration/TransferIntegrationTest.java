package com.pulsepay.wallet_service.integration;

import com.pulsepay.wallet_service.dto.request.CreditWalletRequest;
import com.pulsepay.wallet_service.dto.request.CreateTransferRequest;
import com.pulsepay.wallet_service.dto.request.CreateWalletRequest;
import com.pulsepay.wallet_service.entity.OutboxEvent;
import com.pulsepay.wallet_service.entity.Transfer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import com.pulsepay.wallet_service.entity.Wallet;
import com.pulsepay.wallet_service.enums.OutboxEventStatus;
import com.pulsepay.wallet_service.enums.TransferStatus;
import com.pulsepay.wallet_service.enums.WalletStatus;
import com.pulsepay.wallet_service.repository.OutboxEventRepository;
import com.pulsepay.wallet_service.repository.TransferRepository;
import com.pulsepay.wallet_service.repository.WalletRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
// WHY:
// Tells Spring Boot to start the complete application context
// so that our integration test uses the real Controller,
// Service, Repository, JPA and database configuration.
@Testcontainers
// WHY:
// Tells JUnit that this test class uses Testcontainers.
// JUnit/Testcontainers will manage the container lifecycle.
@AutoConfigureMockMvc
// WHY:
// Gives us MockMvc so we can test our REST API
// without starting a real HTTP server.
public class TransferIntegrationTest {


    // ============================================================
    // TEST DATABASE
    // ============================================================

    /*
     * WHY:
     *
     * We want the integration test to run against a real
     * PostgreSQL database.
     *
     * This allows us to test:
     *
     * Spring Boot
     *      ↓
     * JPA / Hibernate
     *      ↓
     * PostgreSQL
     *
     * instead of using an artificial in-memory database.
     */
    @Container
    @ServiceConnection
    static org.testcontainers.containers.PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(
                    DockerImageName.parse("apache/kafka:4.2.1")
            );

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.kafka.bootstrap-servers",
                kafka::getBootstrapServers
        );
    }

    /*
     * WHY:
     *
     * MockMvc allows us to send HTTP requests to our
     * application just like a real client would.
     */
    @Autowired
    private MockMvc mockMvc;


    /*
     * WHY:
     *
     * JsonMapper converts our Java request objects into JSON
     * and allows us to read JSON returned by the API.
     */
    @Autowired
    private JsonMapper objectMapper;


    /*
     * WHY:
     *
     * We use TransferRepository to verify that the transfer
     * was actually saved into the database.
     */
    @Autowired
    private TransferRepository transferRepository;


    /*
     * WHY:
     *
     * We use WalletRepository to verify the final balances
     * after the transfer.
     *
     * We are NOT using this repository to create the wallets.
     * Wallets are created through the real API.
     */
    @Autowired
    private WalletRepository walletRepository;


    // ============================================================
    // CLEAN DATABASE BEFORE EACH TEST
    // ============================================================
            @Autowired
    private OutboxEventRepository outboxEventRepository;


    @BeforeEach
    void cleanDataBase() {
        outboxEventRepository.deleteAll();

        /*
         * WHY:
         *
         * Transfers must be deleted first because the transfers
         * table has foreign keys pointing to the wallets table.
         */
        transferRepository.deleteAll();

        /*
         * Once transfers are deleted, wallets can safely be deleted.
         */
        walletRepository.deleteAll();
    }


    @Test
    void shouldTransferMoneySuccessfully() throws Exception {


        // ========================================================
        // ARRANGE — CREATE SOURCE ACCOUNT
        // ========================================================

        /*
         * WHY:
         *
         * In the real application, the account ID would come
         * from the authenticated user's security context.
         *
         * Our current controller expects:
         *
         *     @RequestAttribute("accountId")
         *
         * Therefore, the integration test simulates the
         * authenticated account using this UUID.
         */
        UUID sourceAccountId = UUID.randomUUID();


        // ========================================================
        // ARRANGE — CREATE DESTINATION ACCOUNT
        // ========================================================

        /*
         * WHY:
         *
         * We intentionally use a different account ID.
         *
         * This represents a real customer-to-customer transfer:
         *
         * Account A
         *     |
         *     | $30
         *     ↓
         * Account B
         */
        UUID destinationAccountId = UUID.randomUUID();


        // ========================================================
        // ARRANGE — CREATE SOURCE WALLET
        // ========================================================

        /*
         * WHY:
         *
         * We create the wallet through the real Wallet API.
         *
         * This means the test exercises our actual application
         * flow instead of directly inserting test data into
         * the database.
         */
        CreateWalletRequest sourceRequest =
                new CreateWalletRequest();

        sourceRequest.setCurrency("USD");


        MvcResult sourceResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        sourceRequest
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();


        // ========================================================
        // ARRANGE — EXTRACT SOURCE WALLET ID
        // ========================================================

        /*
         * WHY:
         *
         * The application generates the wallet ID.
         *
         * Therefore, we read the generated ID from the API
         * response instead of creating our own UUID.
         */
        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        sourceResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );


        // ========================================================
        // ARRANGE — CREATE DESTINATION WALLET
        // ========================================================

        /*
         * WHY:
         *
         * The destination wallet belongs to another account.
         *
         * It uses USD as well because our current transfer
         * implementation does not support currency conversion.
         */
        CreateWalletRequest destinationRequest =
                new CreateWalletRequest();

        destinationRequest.setCurrency("USD");


        MvcResult destinationResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                destinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        destinationRequest
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();


        // ========================================================
        // ARRANGE — EXTRACT DESTINATION WALLET ID
        // ========================================================

        /*
         * WHY:
         *
         * We need the generated destination wallet ID
         * for our transfer request.
         */
        UUID destinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        destinationResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );


        // ========================================================
        // ARRANGE — FUND SOURCE WALLET
        // ========================================================

        /*
         * WHY:
         *
         * A transfer cannot happen unless the source wallet
         * has sufficient balance.
         *
         * We therefore put $100 into the source wallet first.
         *
         * We use the existing CreditWalletRequest DTO.
         */
        CreditWalletRequest creditRequest =
                new CreditWalletRequest();

        creditRequest.setAmount(
                new BigDecimal("100.00")
        );


        /*
         * WHY:
         *
         * We use the existing credit API instead of directly
         * modifying the wallet balance in the database.
         *
         * We only need to know that the credit succeeded.
         *
         * Therefore, we don't need MvcResult or getResponse()
         * for this request.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/"
                                                + sourceWalletId
                                                + "/credit"
                                )
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                creditRequest
                                        )
                                )
                )
                .andExpect(status().isOk());


        // ========================================================
        // STEP 4: CREATE TRANSFER REQUEST
        // ========================================================

        /*
         * WHY:
         *
         * The client sends:
         *
         * - source wallet ID
         * - destination wallet ID
         * - amount
         *
         * Currency is intentionally NOT sent by the client.
         *
         * Our service gets the trusted currency from the
         * source wallet in the database.
         */
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("30.00")
                );


        // ========================================================
        // STEP 5: PERFORM TRANSFER
        // ========================================================

        /*
         * WHY:
         *
         * sourceAccountId represents the authenticated customer.
         *
         * The service must verify that this customer actually
         * owns the source wallet.
         */
        MvcResult transferResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/transfers")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        transferRequest
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())


                        /*
                         * WHY:
                         *
                         * These assertions verify that the REST API
                         * returned the expected transfer information.
                         */
                        .andExpect(
                                jsonPath("$.transferId")
                                        .exists()
                        )

                        .andExpect(
                                jsonPath("$.sourceWalletId")
                                        .value(
                                                sourceWalletId.toString()
                                        )
                        )

                        .andExpect(
                                jsonPath("$.destinationWalletId")
                                        .value(
                                                destinationWalletId.toString()
                                        )
                        )

                        .andExpect(
                                jsonPath("$.amount")
                                        .value(30.00)
                        )

                        .andExpect(
                                jsonPath("$.currency")
                                        .value("USD")
                        )

                        .andExpect(
                                jsonPath("$.status")
                                        .value("COMPLETED")
                        )

                        .andReturn();


        // ========================================================
        // STEP 6: EXTRACT TRANSFER ID
        // ========================================================

        /*
         * WHY:
         *
         * We need the transfer ID so that we can find the
         * transfer record directly in the database and verify
         * that it was persisted.
         */
        UUID transferId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        transferResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("transferId")
                                .asText()
                );


        // ========================================================
        // STEP 7: VERIFY WALLET BALANCES
        // ========================================================

        /*
         * WHY:
         *
         * Before the transfer:
         *
         * Source      = $100
         * Destination = $0
         *
         * Transfer    = $30
         *
         * Therefore:
         *
         * Source      = $70
         * Destination = $30
         */
        Wallet sourceWallet =
                walletRepository
                        .findById(sourceWalletId)
                        .orElseThrow();


        Wallet destinationWallet =
                walletRepository
                        .findById(destinationWalletId)
                        .orElseThrow();


        assertThat(sourceWallet.getBalance())
                .isEqualByComparingTo("70.00");


        assertThat(destinationWallet.getBalance())
                .isEqualByComparingTo("30.00");


        // ========================================================
        // STEP 8: VERIFY TRANSFER DATABASE RECORD
        // ========================================================

        /*
         * WHY:
         *
         * A transfer is not complete just because the wallet
         * balances changed.
         *
         * We also need a durable transfer record.
         *
         * This record will later support:
         *
         * - transaction history
         * - reconciliation
         * - auditing
         * - notifications
         * - Kafka events
         */
        Transfer transfer =
                transferRepository
                        .findById(transferId)
                        .orElseThrow();


        assertThat(transfer.getSourceWalletId())
                .isEqualTo(sourceWalletId);


        assertThat(transfer.getDestinationWalletId())
                .isEqualTo(destinationWalletId);


        assertThat(transfer.getAmount())
                .isEqualByComparingTo("30.00");


        assertThat(transfer.getCurrency())
                .isEqualTo("USD");


        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.COMPLETED);
    }
    @Test
    void shouldRejectTransferWhenSourceWalletDoesNotBelongToAuthenticatedAccount()throws Exception{
        // ============================================================
        // STEP 1 — CREATE THE REAL OWNER
        // ============================================================
        UUID walletOwnerAccountId = UUID.randomUUID();
        /*
         * WHY:
         *
         * We need a real wallet in PostgreSQL.
         *
         * This wallet belongs to walletOwnerAccountId.
         *
         * Later, we will deliberately authenticate as a DIFFERENT
         * account and try to use this wallet as the source.
         */
        CreateWalletRequest walletRequest = new CreateWalletRequest();
        walletRequest.setCurrency("USD");
        MvcResult walletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets") // Call wallet creation endpoint.

                                        .contentType(MediaType.APPLICATION_JSON)
                                        // Tell Spring that our request body is JSON.

                                        .content(
                                                objectMapper.writeValueAsString(walletRequest)
                                        )
                                        // Convert the Java request object into JSON.

                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                walletOwnerAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                // Simulate the authenticated account.
                                //
                                // We are saying:
                                // "The currently authenticated user is the owner."

                        )
                        .andExpect(status().isCreated())
                        // The wallet creation should return HTTP 201.

                        .andReturn();
        // Keep the HTTP response because we need the generated walletId.
        // ============================================================
        // STEP 2 — EXTRACT THE CREATED SOURCE WALLET ID
        // ============================================================

        /*
         * The wallet ID is generated by the application.
         *
         * We therefore cannot know it beforehand.
         *
         * We read it from the API response so we can use the
         * actual wallet in our transfer request.
         */
        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        walletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );
        // ============================================================
        // STEP 3 — CREATE A DIFFERENT AUTHENTICATED ACCOUNT
        // ============================================================

        /*
         * This is the key setup for this test.
         *
         * walletOwnerAccountId owns the source wallet.
         *
         * authenticatedAccountId will try to use that wallet.
         *
         * They MUST be different.
         */
        UUID authenticatedAccountId = UUID.randomUUID();


        // ============================================================
        // STEP 4 — CREATE THE TRANSFER REQUEST
        // ============================================================

        /*
         * We create a transfer using the wallet owned by Account A.
         *
         * But the authenticated user will be Account B.
         *
         * Therefore this request should be rejected.
         */
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,       // Wallet owned by Account A.
                        UUID.randomUUID(),    // Random destination; source check should fail first.
                        new BigDecimal("50.00") // Amount the attacker is trying to transfer.
                );


        // ============================================================
        // STEP 5 — SEND THE TRANSFER REQUEST AS ACCOUNT B
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                // Call the real transfer endpoint.

                                .contentType(MediaType.APPLICATION_JSON)
                                // Tell Spring the request body is JSON.

                                .content(
                                        objectMapper.writeValueAsString(transferRequest)
                                )
                                // Convert CreateTransferRequest into JSON.

                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        authenticatedAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                        // IMPORTANT:
                        //
                        // We intentionally authenticate as Account B.
                        //
                        // Account B does NOT own sourceWalletId.
                )

                // ============================================================
                // STEP 6 — VERIFY THE API REJECTED THE REQUEST
                // ============================================================

                .andExpect(status().isNotFound())
                /*
                 * WHY 404?
                 *
                 * Our service calls:
                 *
                 * findByIdAndAccountId(sourceWalletId, accountId)
                 *
                 * Since Account B does not own the wallet,
                 * the repository returns empty.
                 *
                 * That becomes WalletNotFoundException.
                 *
                 * GlobalExceptionHandler converts that into HTTP 404.
                 */

                .andExpect(jsonPath("$.code")
                        .value("WALLET_NOT_FOUND"));
        /*
         * HTTP status alone is not enough.
         *
         * We also verify our API's business error code.
         *
         * This makes sure the correct exception path was executed.
         */

// ============================================================
        // STEP 7 — VERIFY DATABASE WAS NOT CHANGED
        // ============================================================

        /*
         * The request was rejected.
         *
         * Therefore the source wallet's money must remain untouched.
         *
         * We read the actual wallet from PostgreSQL.
         */
        Wallet sourceWallet =
                walletRepository
                        .findById(sourceWalletId)
                        .orElseThrow();

        /*
         * The wallet was newly created and never funded.
         *
         * Therefore its balance should still be ZERO.
         *
         * If this assertion failed, something changed the wallet
         * even though the transfer should have been rejected.
         */
        assertThat(sourceWallet.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);


        /*
         * No transfer should have been created.
         *
         * The request should have failed BEFORE:
         *
         * Transfer transfer = ...
         *
         * and before:
         *
         * transferRepository.save(...)
         */
        assertThat(transferRepository.count())
                .isZero();

    }
    @Test
    void shouldRejectTransferWhenDestinationWalletDoesNotExist()
            throws Exception {

        // ============================================================
        // STEP 1 — CREATE THE AUTHENTICATED ACCOUNT
        // ============================================================

        // This represents the user who is performing the transfer.
        UUID accountId = UUID.randomUUID();


        // ============================================================
        // STEP 2 — CREATE SOURCE WALLET
        // ============================================================

        // CreateWalletRequest only contains currency.
        CreateWalletRequest walletRequest = new CreateWalletRequest();

        // The wallet will be a USD wallet.
        walletRequest.setCurrency("USD");

        /*
         * Create the wallet through the real HTTP endpoint.
         *
         * WHY:
         * We want the test to use the same application flow
         * that a real client would use.
         */
        MvcResult walletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")

                                        // Request body is JSON.
                                        .contentType(MediaType.APPLICATION_JSON)

                                        // Convert Java object → JSON.
                                        .content(
                                                objectMapper.writeValueAsString(walletRequest)
                                        )

                                        // Simulate the authenticated account.
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                accountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();


        // ============================================================
        // STEP 3 — GET THE REAL SOURCE WALLET ID
        // ============================================================

        /*
         * The application generated the wallet UUID.
         *
         * We cannot guess that UUID, so we extract it from
         * the API response.
         */
        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        walletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );


        // ============================================================
        // STEP 4 — CREATE A NON-EXISTING DESTINATION ID
        // ============================================================

        /*
         * This UUID is intentionally NOT inserted into the database.
         *
         * Therefore, when the service searches for it,
         * it should not find a destination wallet.
         */
        UUID destinationWalletId = UUID.randomUUID();


        // ============================================================
        // STEP 5 — CREATE TRANSFER REQUEST
        // ============================================================

        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("50.00")
                );


        // ============================================================
        // STEP 6 — CALL TRANSFER API
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")

                                // Tell Spring this is JSON.
                                .contentType(MediaType.APPLICATION_JSON)

                                // Convert request object → JSON.
                                .content(
                                        objectMapper.writeValueAsString(transferRequest)
                                )

                                // Authenticate as the source wallet owner.
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                )

                // Destination does not exist.
                .andExpect(status().isNotFound())

                // Verify the correct application error code.
                .andExpect(jsonPath("$.code")
                        .value("WALLET_NOT_FOUND"));


        // ============================================================
        // STEP 7 — VERIFY DATABASE WAS NOT CHANGED
        // ============================================================

        /*
         * Because the transfer failed before money movement,
         * the source wallet should still exist unchanged.
         */
        Wallet sourceWallet =
                walletRepository
                        .findById(sourceWalletId)
                        .orElseThrow();

        // The wallet was never funded, so balance remains zero.
        assertThat(sourceWallet.getBalance())
                .isEqualByComparingTo("0.00");

        // No Transfer entity should have been persisted.
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenSourceAndDestinationAreSame()
            throws Exception {

        // ============================================================
        // STEP 1 — CREATE ACCOUNT
        // ============================================================

        // This account owns the wallet.
        UUID accountId = UUID.randomUUID();


        // ============================================================
        // STEP 2 — CREATE WALLET
        // ============================================================

        CreateWalletRequest walletRequest = new CreateWalletRequest();

        // Create a USD wallet.
        walletRequest.setCurrency("USD");

        MvcResult walletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(walletRequest)
                                        )
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                accountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();


        // Extract the generated wallet ID.
        UUID walletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        walletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );


        // ============================================================
        // STEP 3 — USE THE SAME WALLET FOR BOTH SIDES
        // ============================================================

        /*
         * This is the actual scenario we want to test:
         *
         * source = walletId
         * destination = walletId
         *
         * Therefore the wallet is transferring to itself.
         */
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        walletId,
                        walletId,
                        new BigDecimal("50.00")
                );


        // ============================================================
        // STEP 4 — CALL API
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(transferRequest)
                                )
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                )

                // Same-wallet transfer violates business rules.
                .andExpect(status().isBadRequest())

                // Verify our InvalidTransferException handler was used.
                .andExpect(jsonPath("$.code")
                        .value("INVALID_TRANSFER"));


        // ============================================================
        // STEP 5 — VERIFY NOTHING CHANGED
        // ============================================================

        Wallet wallet =
                walletRepository
                        .findById(walletId)
                        .orElseThrow();

        // No money should have been moved.
        assertThat(wallet.getBalance())
                .isEqualByComparingTo("0.00");

        // No transfer should have been created.
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenCurrenciesDoNotMatch()
            throws Exception {

        // ============================================================
        // STEP 1 — CREATE TWO DIFFERENT ACCOUNTS
        // ============================================================

        // Account that owns the source wallet.
        UUID sourceAccountId = UUID.randomUUID();

        // Account that owns the destination wallet.
        UUID destinationAccountId = UUID.randomUUID();


        // ============================================================
        // STEP 2 — CREATE USD SOURCE WALLET
        // ============================================================

        CreateWalletRequest sourceRequest = new CreateWalletRequest();

        // Source wallet uses USD.
        sourceRequest.setCurrency("USD");

        MvcResult sourceResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(sourceRequest)
                                        )
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        // Extract source wallet ID.
        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        sourceResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );


        // ============================================================
        // STEP 3 — CREATE EUR DESTINATION WALLET
        // ============================================================

        CreateWalletRequest destinationRequest =
                new CreateWalletRequest();

        // Destination wallet uses EUR.
        destinationRequest.setCurrency("EUR");

        MvcResult destinationResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        destinationRequest
                                                )
                                        )
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                destinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        // Extract destination wallet ID.
        UUID destinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        destinationResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );


        // ============================================================
        // STEP 4 — CREATE TRANSFER
        // ============================================================

        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("50.00")
                );


        // ============================================================
        // STEP 5 — ATTEMPT USD → EUR TRANSFER
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(transferRequest)
                                )

                                // Authenticate as source wallet owner.
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())


                )

                // Different currencies violate current business rules.
                .andExpect(status().isBadRequest())

                .andExpect(jsonPath("$.code")
                        .value("INVALID_TRANSFER"));


        // ============================================================
        // STEP 6 — VERIFY NO TRANSFER WAS CREATED
        // ============================================================

        /*
         * The validation occurs BEFORE debit() and credit().
         *
         * Therefore there should be no transfer record.
         */
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenSourceWalletIsNotActive()
            throws Exception {

        // ============================================================
        // STEP 1 — CREATE SOURCE ACCOUNT
        // ============================================================

        UUID sourceAccountId = UUID.randomUUID();

        UUID destinationAccountId = UUID.randomUUID();


        // ============================================================
        // STEP 2 — CREATE SOURCE WALLET
        // ============================================================

        CreateWalletRequest sourceRequest = new CreateWalletRequest();

        sourceRequest.setCurrency("USD");

        MvcResult sourceResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        sourceRequest
                                                )
                                        )
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )

                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        sourceResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );


        // ============================================================
        // STEP 3 — CREATE DESTINATION WALLET
        // ============================================================

        CreateWalletRequest destinationRequest =
                new CreateWalletRequest();

        destinationRequest.setCurrency("USD");

        MvcResult destinationResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        destinationRequest
                                                )
                                        )
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                destinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID destinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        destinationResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );


        // ============================================================
        // STEP 4 — CHANGE SOURCE WALLET TO SUSPENDED
        // ============================================================

        /*
         * WHY:
         *
         * We need to create the wallet normally first because the API
         * creates it as ACTIVE.
         *
         * Then we directly modify its state in the database because
         * the purpose of this test is to test transfer behavior when
         * the wallet is already suspended.
         */
        Wallet sourceWallet =
                walletRepository
                        .findById(sourceWalletId)
                        .orElseThrow();

        // Put the wallet into the state we're trying to test.
        sourceWallet.setStatus(WalletStatus.SUSPENDED);

        // Persist the state change.
        walletRepository.saveAndFlush(sourceWallet);


        // ============================================================
        // STEP 5 — CREATE TRANSFER REQUEST
        // ============================================================

        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("50.00")
                );


        // ============================================================
        // STEP 6 — ATTEMPT TRANSFER
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                )

                .andExpect(status().isConflict())

                .andExpect(jsonPath("$.code")
                        .value("WALLET_NOT_ACTIVE"));


        // ============================================================
        // STEP 7 — VERIFY NO TRANSFER WAS CREATED
        // ============================================================

        assertThat(transferRepository.count())
                .isZero();


        // Verify source wallet is still suspended.
        Wallet unchangedSourceWallet =
                walletRepository
                        .findById(sourceWalletId)
                        .orElseThrow();

        assertThat(unchangedSourceWallet.getStatus())
                .isEqualTo(WalletStatus.SUSPENDED);
    }
    @Test
    void shouldRejectTransferWhenDestinationWalletIsNotActive()
            throws Exception {

        UUID sourceAccountId = UUID.randomUUID();

        UUID destinationAccountId = UUID.randomUUID();


        // ============================================================
        // CREATE SOURCE WALLET
        // ============================================================

        CreateWalletRequest sourceRequest = new CreateWalletRequest();

        sourceRequest.setCurrency("USD");

        MvcResult sourceResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        sourceRequest
                                                )
                                        )
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId
                                                                        .toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        sourceResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );


        // ============================================================
        // CREATE DESTINATION WALLET
        // ============================================================

        CreateWalletRequest destinationRequest =
                new CreateWalletRequest();

        destinationRequest.setCurrency("USD");

        MvcResult destinationResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        destinationRequest
                                                )
                                        )
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                destinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID destinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        destinationResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );


        // ============================================================
        // MAKE DESTINATION WALLET SUSPENDED
        // ============================================================

        /*
         * We directly prepare the database state because there is no
         * need to test wallet suspension itself here.
         *
         * This test is specifically about transfer behavior.
         */
        Wallet destinationWallet =
                walletRepository
                        .findById(destinationWalletId)
                        .orElseThrow();

        destinationWallet.setStatus(WalletStatus.SUSPENDED);

        walletRepository.saveAndFlush(destinationWallet);


        // ============================================================
        // CREATE TRANSFER REQUEST
        // ============================================================

        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("50.00")
                );


        // ============================================================
        // ATTEMPT TRANSFER
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                )

                .andExpect(status().isConflict())

                .andExpect(jsonPath("$.code")
                        .value("WALLET_NOT_ACTIVE"));


        // No transfer should have been persisted.
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenAmountIsInvalid()
            throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        UUID accountId = UUID.randomUUID();

        /*
         * We don't need real wallets here.
         *
         * WHY?
         *
         * The amount validation should happen before the service
         * needs to look up wallets.
         *
         * Therefore we can focus specifically on request validation.
         */
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        BigDecimal.ZERO
                );


        // ============================================================
        // ACT
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .contentType(MediaType.APPLICATION_JSON)

                                // Send amount = 0.
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )

                                // Simulate authenticated account.
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                )

                // ============================================================
                // ASSERT
                // ============================================================

                /*
                 * @DecimalMin("0.01") means zero is invalid.
                 *
                 * Therefore Spring should reject the request.
                 */
                .andExpect(status().isBadRequest());


        /*
         * There should be no transfer because validation happened
         * before the service could create one.
         */
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenSourceHasInsufficientBalance()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        /*
         * We use two different accounts because a real transfer can
         * happen between wallets belonging to different accounts.
         */
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        // ---------------------------------------------------------
        // Create SOURCE wallet
        // ---------------------------------------------------------
        //
        // The accountId is NOT inside CreateWalletRequest.
        // Authentication/ownership is represented by requestAttr()
        // in our current test setup.
        //
        MvcResult sourceWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        /*
         * Extract the generated wallet ID from the HTTP response.
         *
         * We need this ID because the transfer API works with
         * wallet IDs.
         */
        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        sourceWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        // ---------------------------------------------------------
        // Create DESTINATION wallet
        // ---------------------------------------------------------
        //
        // Destination belongs to another account.
        //
        MvcResult destinationWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                destinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        /*
         * Extract destination wallet ID from the response.
         */
        UUID destinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        destinationWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        // ---------------------------------------------------------
        // Fund the source wallet with only $20
        // ---------------------------------------------------------
        //
        // We deliberately give the source wallet LESS money than
        // the transfer amount we will request.
        //
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/wallets/{walletId}/credit", sourceWalletId)
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreditWalletRequest(
                                                        new BigDecimal("20.00")
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk());

        // ---------------------------------------------------------
        // Create transfer request for $50
        // ---------------------------------------------------------
        //
        // Source has $20.
        // We are requesting $50.
        //
        // Therefore Wallet.debit() should throw:
        //
        // InsufficientBalanceException
        //
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("50.00")
                );

        // =========================================================
        // ACT + ASSERT HTTP
        // =========================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )

                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INSUFFICIENT_BALANCE"));

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        /*
         * The transfer failed.
         *
         * Therefore the source wallet must still have its original
         * $20 balance.
         */
        Wallet sourceWallet =
                walletRepository.findById(sourceWalletId)
                        .orElseThrow();

        assertThat(sourceWallet.getBalance())
                .isEqualByComparingTo("20.00");

        /*
         * The destination wallet must still have $0.
         *
         * This proves money was NOT accidentally credited when
         * the source wallet could not be debited.
         */
        Wallet destinationWallet =
                walletRepository.findById(destinationWalletId)
                        .orElseThrow();

        assertThat(destinationWallet.getBalance())
                .isEqualByComparingTo("0.00");

        /*
         * No transfer record should exist because the transfer
         * never completed.
         */
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenSourceWalletIsInactive()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        /*
         * We create two different accounts because the destination
         * wallet can belong to another account.
         */
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        // ---------------------------------------------------------
        // Create SOURCE wallet
        // ---------------------------------------------------------

        MvcResult sourceWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        /*
         * Read the wallet ID from the API response.
         *
         * We need this ID later when creating the transfer.
         */
        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        sourceWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        // ---------------------------------------------------------
        // Create DESTINATION wallet
        // ---------------------------------------------------------

        MvcResult destinationWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                destinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        /*
         * Extract destination wallet ID from the response.
         */
        UUID destinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        destinationWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        // ---------------------------------------------------------
        // Fund source wallet
        // ---------------------------------------------------------

        /*
         * We give the source wallet money first.
         *
         * WHY?
         *
         * We want this test to fail specifically because the wallet
         * is inactive — NOT because it has insufficient balance.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/wallets/{walletId}/credit", sourceWalletId)
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreditWalletRequest(
                                                        new BigDecimal("100.00")
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk());

        // ---------------------------------------------------------
        // Suspend the source wallet directly in the database
        // ---------------------------------------------------------

        /*
         * We retrieve the real persisted wallet.
         *
         * Then we use the domain method changeStatus() instead of
         * directly changing the status field.
         *
         * WHY?
         *
         * The Wallet entity owns the rules for status transitions.
         */
        Wallet sourceWallet =
                walletRepository.findById(sourceWalletId)
                        .orElseThrow();

        sourceWallet.changeStatus(
                com.pulsepay.wallet_service.enums.WalletStatus.SUSPENDED
        );

        /*
         * saveAndFlush() forces Hibernate to send the UPDATE to
         * PostgreSQL immediately.
         *
         * WHY?
         *
         * We want to guarantee that the wallet is actually
         * SUSPENDED before the transfer HTTP request starts.
         */
        walletRepository.saveAndFlush(sourceWallet);

        // =========================================================
        // ACT
        // =========================================================

        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("30.00")
                );

        // =========================================================
        // ASSERT HTTP
        // =========================================================

        /*
         * The source wallet is suspended.
         *
         * Therefore Wallet.debit() should throw:
         *
         * WalletNotActiveException
         *
         * GlobalExceptionHandler converts that exception into:
         *
         * HTTP 409
         * code = WALLET_NOT_ACTIVE
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("WALLET_NOT_ACTIVE"));

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        /*
         * The transfer failed.
         *
         * Therefore the source balance must remain $100.
         */
        Wallet persistedSourceWallet =
                walletRepository.findById(sourceWalletId)
                        .orElseThrow();

        assertThat(persistedSourceWallet.getBalance())
                .isEqualByComparingTo("100.00");

        /*
         * The destination wallet must remain at $0.
         */
        Wallet persistedDestinationWallet =
                walletRepository.findById(destinationWalletId)
                        .orElseThrow();

        assertThat(persistedDestinationWallet.getBalance())
                .isEqualByComparingTo("0.00");

        /*
         * No Transfer entity should have been persisted because
         * the transfer was rejected.
         */
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenDestinationWalletIsInactive()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        /*
         * Source and destination belong to different accounts.
         *
         * This proves our test follows the real transfer business
         * rule where the destination can belong to another account.
         */
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        // ---------------------------------------------------------
        // Create SOURCE wallet
        // ---------------------------------------------------------

        MvcResult sourceWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        /*
         * Extract the generated source wallet ID from the API response.
         */
        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        sourceWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        // ---------------------------------------------------------
        // Create DESTINATION wallet
        // ---------------------------------------------------------

        MvcResult destinationWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                destinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        /*
         * Extract the generated destination wallet ID.
         */
        UUID destinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        destinationWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        // ---------------------------------------------------------
        // Fund source wallet with $100
        // ---------------------------------------------------------

        /*
         * We need enough money in the source wallet.
         *
         * WHY?
         *
         * Otherwise the transfer could fail because of
         * insufficient balance instead of because the destination
         * wallet is inactive.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/wallets/{walletId}/credit", sourceWalletId)
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreditWalletRequest(
                                                        new BigDecimal("100.00")
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk());

        // ---------------------------------------------------------
        // Make destination wallet SUSPENDED
        // ---------------------------------------------------------

        /*
         * Load the actual persisted destination wallet.
         */
        Wallet destinationWallet =
                walletRepository.findById(destinationWalletId)
                        .orElseThrow();

        /*
         * Use the domain method to change the wallet status.
         *
         * WHY?
         *
         * Wallet owns its status-transition rules.
         */
        destinationWallet.changeStatus(
                com.pulsepay.wallet_service.enums.WalletStatus.SUSPENDED
        );

        /*
         * Force the status change into PostgreSQL before the transfer
         * request begins.
         */
        walletRepository.saveAndFlush(destinationWallet);

        // =========================================================
        // ACT
        // =========================================================

        /*
         * Attempt to transfer $30 from the active source wallet
         * to the suspended destination wallet.
         */
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("30.00")
                );

        // =========================================================
        // ASSERT HTTP
        // =========================================================

        /*
         * The destination wallet is not active.
         *
         * Therefore destinationWallet.credit() should throw:
         *
         * WalletNotActiveException
         *
         * GlobalExceptionHandler converts it into:
         *
         * HTTP 409
         * code = WALLET_NOT_ACTIVE
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("WALLET_NOT_ACTIVE"));

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        /*
         * IMPORTANT:
         *
         * sourceWallet.debit(30) happened BEFORE
         * destinationWallet.credit(30).
         *
         * If @Transactional rollback is working correctly,
         * the source wallet must still have $100.
         *
         * If we found $70 here, we would have a serious financial
         * consistency bug.
         */
        Wallet persistedSourceWallet =
                walletRepository.findById(sourceWalletId)
                        .orElseThrow();

        assertThat(persistedSourceWallet.getBalance())
                .isEqualByComparingTo("100.00");

        /*
         * Destination must remain at $0 because its credit operation
         * failed.
         */
        Wallet persistedDestinationWallet =
                walletRepository.findById(destinationWalletId)
                        .orElseThrow();

        assertThat(persistedDestinationWallet.getBalance())
                .isEqualByComparingTo("0.00");

        /*
         * The destination should still be suspended.
         */
        assertThat(persistedDestinationWallet.getStatus())
                .isEqualTo(
                        com.pulsepay.wallet_service.enums.WalletStatus.SUSPENDED
                );

        /*
         * No transfer record should exist.
         *
         * The transfer was never successfully completed.
         */
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenAmountIsZero()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        /*
         * We do not need to create real wallets for this test.
         *
         * WHY?
         *
         * This test is specifically testing DTO validation:
         *
         * @DecimalMin("0.01")
         *
         * The request should be rejected before the transfer
         * service tries to find either wallet.
         */
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        BigDecimal.ZERO
                );

        // =========================================================
        // ACT + ASSERT HTTP
        // =========================================================

        /*
         * Send the invalid request to the real controller through
         * MockMvc.
         *
         * Because the controller has @Valid, Spring should validate
         * the request before calling TransferService.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        UUID.randomUUID().toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        /*
         * No transfer should have been created.
         *
         * WHY?
         *
         * The request failed during validation, so the business
         * operation should never have started.
         */
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenAmountIsNull()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        /*
         * amount = null violates @NotNull on CreateTransferRequest.
         *
         * We still provide sourceWalletId and destinationWalletId
         * because we want this test to isolate the amount validation.
         */
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null
                );

        // =========================================================
        // ACT + ASSERT HTTP
        // =========================================================

        /*
         * Spring's @Valid validation should reject the request
         * before TransferService is called.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        UUID.randomUUID().toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        /*
         * Validation failed, so no transfer should have been
         * persisted.
         */
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenSourceWalletIdIsNull()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        /*
         * sourceWalletId = null violates @NotNull.
         *
         * destinationWalletId and amount are valid because we want
         * this test to specifically target sourceWalletId validation.
         */
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        null,
                        UUID.randomUUID(),
                        new BigDecimal("30.00")
                );

        // =========================================================
        // ACT + ASSERT HTTP
        // =========================================================

        /*
         * @Valid on the controller triggers Bean Validation.
         *
         * Because sourceWalletId is null, Spring should reject the
         * request before TransferService.createTransfer() executes.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        UUID.randomUUID().toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        /*
         * Since validation happened before the business operation,
         * no Transfer should have been persisted.
         */
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldRejectTransferWhenDestinationWalletIdIsNull()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        /*
         * destinationWalletId = null violates @NotNull.
         *
         * The other two fields are valid so this test isolates
         * destinationWalletId validation.
         */
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        UUID.randomUUID(),
                        null,
                        new BigDecimal("30.00")
                );

        // =========================================================
        // ACT + ASSERT HTTP
        // =========================================================

        /*
         * Spring's validation layer should reject the request
         * before TransferService is called.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        UUID.randomUUID().toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        /*
         * No business operation should have occurred.
         */
        assertThat(transferRepository.count())
                .isZero();
    }
    @Test
    void shouldReturnSameTransferForRetryWithSameIdempotencyKey()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        /*
         * We use two different accounts because a real transfer can
         * happen between wallets belonging to different accounts.
         */
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        // ---------------------------------------------------------
        // Create SOURCE wallet
        // ---------------------------------------------------------
        //
        MvcResult sourceWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        /*
         * Extract the generated source wallet ID.
         */
        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        sourceWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        // ---------------------------------------------------------
        // Create DESTINATION wallet
        // ---------------------------------------------------------
        //
        MvcResult destinationWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                destinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        /*
         * Extract the generated destination wallet ID.
         */
        UUID destinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        destinationWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        // ---------------------------------------------------------
        // Fund SOURCE wallet with $100
        // ---------------------------------------------------------
        //
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/{walletId}/credit",
                                        sourceWalletId
                                )
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreditWalletRequest(
                                                        new BigDecimal("100.00")
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk());

        // ---------------------------------------------------------
        // Create transfer request
        // ---------------------------------------------------------

        /*
         * Source has $100.
         *
         * We will transfer $30.
         */
        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("30.00")
                );

        /*
         * We deliberately use the SAME idempotency key
         * for both requests.
         */
        String idempotencyKey = UUID.randomUUID().toString();

        // =========================================================
        // ACT — FIRST REQUEST
        // =========================================================

        MvcResult firstTransferResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/transfers")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .header(
                                                "Idempotency-Key",
                                                idempotencyKey
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        transferRequest
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status")
                                .value("COMPLETED"))
                        .andReturn();

        /*
         * Extract the transfer ID created by the FIRST request.
         */
        UUID firstTransferId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        firstTransferResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("transferId")
                                .asText()
                );

        // =========================================================
        // ACT — SECOND REQUEST / RETRY
        // =========================================================

        MvcResult secondTransferResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/transfers")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .header(
                                                "Idempotency-Key",
                                                idempotencyKey
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        transferRequest
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status")
                                .value("COMPLETED"))
                        .andReturn();

        /*
         * Extract the transfer ID returned by the SECOND request.
         */
        UUID secondTransferId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        secondTransferResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("transferId")
                                .asText()
                );

        // =========================================================
        // ASSERT — SAME TRANSFER
        // =========================================================

        /*
         * The second request must return the ORIGINAL transfer.
         *
         * It must NOT create a new transfer.
         */
        assertThat(secondTransferId)
                .isEqualTo(firstTransferId);

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        /*
         * The first request moved $30.
         *
         * The second request was only a retry.
         *
         * Therefore the source must still have $70.
         */
        Wallet sourceWallet =
                walletRepository.findById(sourceWalletId)
                        .orElseThrow();

        assertThat(sourceWallet.getBalance())
                .isEqualByComparingTo("70.00");

        /*
         * The destination received $30 only once.
         */
        Wallet destinationWallet =
                walletRepository.findById(destinationWalletId)
                        .orElseThrow();

        assertThat(destinationWallet.getBalance())
                .isEqualByComparingTo("30.00");

        /*
         * Only ONE transfer record should exist.
         *
         * This proves the retry did not create another transfer.
         */
        assertThat(transferRepository.count())
                .isEqualTo(1);
    }
    @Test
    void shouldRejectTransferWhenIdempotencyKeyIsReusedForDifferentRequest()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        MvcResult sourceWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        sourceWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        MvcResult destinationWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                destinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID destinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        destinationWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/{walletId}/credit",
                                        sourceWalletId
                                )
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreditWalletRequest(
                                                        new BigDecimal("100.00")
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk());

        String idempotencyKey = UUID.randomUUID().toString();

        CreateTransferRequest firstTransferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("30.00")
                );

        // =========================================================
        // ACT — FIRST REQUEST
        // =========================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                firstTransferRequest
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status")
                        .value("COMPLETED"));

        // =========================================================
        // ACT — SECOND REQUEST WITH SAME KEY
        // BUT DIFFERENT AMOUNT
        // =========================================================

        CreateTransferRequest secondTransferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("50.00")
                );

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                secondTransferRequest
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("IDEMPOTENCY_KEY_REUSE"));

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        Wallet sourceWallet =
                walletRepository.findById(sourceWalletId)
                        .orElseThrow();

        assertThat(sourceWallet.getBalance())
                .isEqualByComparingTo("70.00");

        Wallet destinationWallet =
                walletRepository.findById(destinationWalletId)
                        .orElseThrow();

        assertThat(destinationWallet.getBalance())
                .isEqualByComparingTo("30.00");

        assertThat(transferRepository.count())
                .isEqualTo(1);
    }
    @Test
    void shouldAllowRetryWithSameIdempotencyKeyAfterFailedTransfer()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        MvcResult sourceWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID sourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        sourceWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        MvcResult destinationWalletResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                destinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID destinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        destinationWalletResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/{walletId}/credit",
                                        sourceWalletId
                                )
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreditWalletRequest(
                                                        new BigDecimal("100.00")
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk());

        Wallet destinationWallet =
                walletRepository.findById(destinationWalletId)
                        .orElseThrow();

        destinationWallet.changeStatus(WalletStatus.SUSPENDED);

        walletRepository.saveAndFlush(destinationWallet);

        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWalletId,
                        destinationWalletId,
                        new BigDecimal("30.00")
                );

        String idempotencyKey = UUID.randomUUID().toString();

        // =========================================================
        // ACT — FIRST REQUEST FAILS
        // =========================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        sourceAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("WALLET_NOT_ACTIVE"));

        // =========================================================
        // FIX THE BUSINESS CONDITION
        // =========================================================

        destinationWallet =
                walletRepository.findById(destinationWalletId)
                        .orElseThrow();

        destinationWallet.changeStatus(WalletStatus.ACTIVE);

        walletRepository.saveAndFlush(destinationWallet);

        // =========================================================
        // ACT — SECOND REQUEST WITH SAME KEY
        // =========================================================

        MvcResult retryResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/transfers")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .header(
                                                "Idempotency-Key",
                                                idempotencyKey
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        transferRequest
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status")
                                .value("COMPLETED"))
                        .andReturn();

        UUID transferId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        retryResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("transferId")
                                .asText()
                );

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        Wallet sourceWallet =
                walletRepository.findById(sourceWalletId)
                        .orElseThrow();

        assertThat(sourceWallet.getBalance())
                .isEqualByComparingTo("70.00");

        destinationWallet =
                walletRepository.findById(destinationWalletId)
                        .orElseThrow();

        assertThat(destinationWallet.getBalance())
                .isEqualByComparingTo("30.00");

        assertThat(destinationWallet.getStatus())
                .isEqualTo(WalletStatus.ACTIVE);

        assertThat(transferRepository.count())
                .isEqualTo(1);

        assertThat(transferRepository.findById(transferId))
                .isPresent();
    }
    @Test
    void shouldAllowSameIdempotencyKeyForDifferentAccounts()
            throws Exception {

        // =========================================================
        // ARRANGE
        // =========================================================

        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();

        UUID firstDestinationAccountId = UUID.randomUUID();
        UUID secondDestinationAccountId = UUID.randomUUID();

        MvcResult firstSourceResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                firstAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID firstSourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        firstSourceResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        MvcResult firstDestinationResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                firstDestinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID firstDestinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        firstDestinationResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        MvcResult secondSourceResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                secondAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID secondSourceWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        secondSourceResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        MvcResult secondDestinationResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                secondDestinationAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        new CreateWalletRequest("USD")
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn();

        UUID secondDestinationWalletId =
                UUID.fromString(
                        objectMapper
                                .readTree(
                                        secondDestinationResult
                                                .getResponse()
                                                .getContentAsString()
                                )
                                .get("id")
                                .asText()
                );

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/{walletId}/credit",
                                        firstSourceWalletId
                                )
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        firstAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreditWalletRequest(
                                                        new BigDecimal("100.00")
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/{walletId}/credit",
                                        secondSourceWalletId
                                )
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        secondAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CreditWalletRequest(
                                                        new BigDecimal("100.00")
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk());

        String sameIdempotencyKey = UUID.randomUUID().toString();

        CreateTransferRequest firstTransferRequest =
                new CreateTransferRequest(
                        firstSourceWalletId,
                        firstDestinationWalletId,
                        new BigDecimal("30.00")
                );

        CreateTransferRequest secondTransferRequest =
                new CreateTransferRequest(
                        secondSourceWalletId,
                        secondDestinationWalletId,
                        new BigDecimal("40.00")
                );

        // =========================================================
        // ACT — FIRST ACCOUNT
        // =========================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        firstAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        sameIdempotencyKey
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                firstTransferRequest
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status")
                        .value("COMPLETED"));

        // =========================================================
        // ACT — SECOND ACCOUNT WITH SAME KEY
        // =========================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        secondAccountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        sameIdempotencyKey
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                secondTransferRequest
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status")
                        .value("COMPLETED"));

        // =========================================================
        // ASSERT DATABASE
        // =========================================================

        Wallet firstSourceWallet =
                walletRepository.findById(firstSourceWalletId)
                        .orElseThrow();

        assertThat(firstSourceWallet.getBalance())
                .isEqualByComparingTo("70.00");

        Wallet firstDestinationWallet =
                walletRepository.findById(firstDestinationWalletId)
                        .orElseThrow();

        assertThat(firstDestinationWallet.getBalance())
                .isEqualByComparingTo("30.00");

        Wallet secondSourceWallet =
                walletRepository.findById(secondSourceWalletId)
                        .orElseThrow();

        assertThat(secondSourceWallet.getBalance())
                .isEqualByComparingTo("60.00");

        Wallet secondDestinationWallet =
                walletRepository.findById(secondDestinationWalletId)
                        .orElseThrow();

        assertThat(secondDestinationWallet.getBalance())
                .isEqualByComparingTo("40.00");

        assertThat(transferRepository.count())
                .isEqualTo(2);
    }
    @Test
    void shouldCreatePendingOutboxEventWhenTransferSucceeds()
            throws Exception {

        // ============================================================
        // ARRANGE — CREATE TWO ACCOUNTS
        // ============================================================

        UUID sourceAccountId =
                UUID.randomUUID();

        UUID destinationAccountId =
                UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE SOURCE WALLET
        // ============================================================

        Wallet sourceWallet =
                Wallet.builder()
                        .accountId(sourceAccountId)
                        .currency("USD")
                        .balance(new BigDecimal("100.00"))
                        .status(WalletStatus.ACTIVE)
                        .build();

        sourceWallet =
                walletRepository.save(sourceWallet);


        // ============================================================
        // ARRANGE — CREATE DESTINATION WALLET
        // ============================================================

        Wallet destinationWallet =
                Wallet.builder()
                        .accountId(destinationAccountId)
                        .currency("USD")
                        .balance(new BigDecimal("50.00"))
                        .status(WalletStatus.ACTIVE)
                        .build();

        destinationWallet =
                walletRepository.save(destinationWallet);


        // ============================================================
        // ARRANGE — CREATE TRANSFER REQUEST
        // ============================================================

        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWallet.getId(),
                        destinationWallet.getId(),
                        new BigDecimal("25.00")
                );


        String idempotencyKey =
                UUID.randomUUID().toString();


        // ============================================================
        // ACT — CREATE TRANSFER
        // ============================================================

        MvcResult transferResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request
                                        .MockMvcRequestBuilders
                                        .post("/api/v1/transfers")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                sourceAccountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )
                                        .header(
                                                "Idempotency-Key",
                                                idempotencyKey
                                        )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        transferRequest
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andExpect(
                                jsonPath("$.transferId")
                                        .exists()
                        )
                        .andReturn();


        // ============================================================
        // ASSERT — EXTRACT TRANSFER ID
        // ============================================================

        String transferId =
                objectMapper
                        .readTree(
                                transferResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("transferId")
                        .asText();

        UUID transferUuid =
                UUID.fromString(transferId);


        // ============================================================
        // ASSERT — TRANSFER DATABASE STATE
        // ============================================================

        Transfer transfer =
                transferRepository
                        .findById(transferUuid)
                        .orElseThrow();

        assertThat(transfer.getStatus())
                .isEqualTo(
                        TransferStatus.COMPLETED
                );

        assertThat(transfer.getAmount())
                .isEqualByComparingTo(
                        new BigDecimal("25.00")
                );

        assertThat(transfer.getCurrency())
                .isEqualTo("USD");


        // ============================================================
        // ASSERT — SOURCE WALLET BALANCE
        // ============================================================

        Wallet updatedSourceWallet =
                walletRepository
                        .findById(sourceWallet.getId())
                        .orElseThrow();

        assertThat(updatedSourceWallet.getBalance())
                .isEqualByComparingTo(
                        new BigDecimal("75.00")
                );


        // ============================================================
        // ASSERT — DESTINATION WALLET BALANCE
        // ============================================================

        Wallet updatedDestinationWallet =
                walletRepository
                        .findById(destinationWallet.getId())
                        .orElseThrow();

        assertThat(updatedDestinationWallet.getBalance())
                .isEqualByComparingTo(
                        new BigDecimal("75.00")
                );


        // ============================================================
        // ASSERT — OUTBOX EVENT
        // ============================================================

        List<OutboxEvent> outboxEvents =
                outboxEventRepository.findAll();

        assertThat(outboxEvents)
                .hasSize(1);


        OutboxEvent outboxEvent =
                outboxEvents.get(0);


        // ============================================================
        // ASSERT — OUTBOX AGGREGATE
        // ============================================================

        assertThat(outboxEvent.getAggregateType())
                .isEqualTo("TRANSFER");

        assertThat(outboxEvent.getAggregateId())
                .isEqualTo(transferUuid);


        // ============================================================
        // ASSERT — EVENT TYPE
        // ============================================================

        assertThat(outboxEvent.getEventType())
                .isEqualTo("TRANSFER_COMPLETED");


        // ============================================================
        // ASSERT — EVENT STATUS
        // ============================================================

        assertThat(outboxEvent.getStatus())
                .isEqualTo(
                        OutboxEventStatus.PENDING
                );


        // ============================================================
        // ASSERT — EVENT PAYLOAD
        // ============================================================

        assertThat(outboxEvent.getPayload())
                .contains(
                        sourceWallet.getId().toString()
                );

        assertThat(outboxEvent.getPayload())
                .contains(
                        destinationWallet.getId().toString()
                );

        assertThat(outboxEvent.getPayload())
                .contains("25.00");

        assertThat(outboxEvent.getPayload())
                .contains("USD");
    }
    @Test
    void shouldNotCreateOutboxEventWhenTransferFails()
            throws Exception {

        // =========================
        // Arrange
        // =========================

        UUID accountId = UUID.randomUUID();

        Wallet sourceWallet =
                Wallet.builder()
                        .accountId(accountId)
                        .currency("USD")
                        .balance(new BigDecimal("10.00"))
                        .status(WalletStatus.ACTIVE)
                        .build();


        Wallet destinationWallet =
                Wallet.builder()
                        .accountId(UUID.randomUUID())
                        .currency("USD")
                        .balance(new BigDecimal("50.00"))
                        .status(WalletStatus.ACTIVE)
                        .build();



        sourceWallet =
                walletRepository.save(sourceWallet);

        destinationWallet =
                walletRepository.save(destinationWallet);

        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWallet.getId(),
                        destinationWallet.getId(),
                        new BigDecimal("25.00")
                );

        String idempotencyKey =
                UUID.randomUUID().toString();


        // =========================
        // Act
        // =========================

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));


        // =========================
        // Assert
        // =========================

        Wallet reloadedSourceWallet =
                walletRepository
                        .findById(sourceWallet.getId())
                        .orElseThrow();

        Wallet reloadedDestinationWallet =
                walletRepository
                        .findById(destinationWallet.getId())
                        .orElseThrow();

        assertThat(reloadedSourceWallet.getBalance())
                .isEqualByComparingTo("10.00");

        assertThat(reloadedDestinationWallet.getBalance())
                .isEqualByComparingTo("50.00");

        assertThat(transferRepository.findAll())
                .isEmpty();

        assertThat(outboxEventRepository.findAll())
                .isEmpty();
    }
    @Test
    void shouldStoreCorrectTransferDataInOutboxPayload()
            throws Exception {

        // =========================
        // Arrange
        // =========================

        UUID accountId = UUID.randomUUID();

        Wallet sourceWallet =
                Wallet.builder()
                        .accountId(accountId)
                        .currency("USD")
                        .balance(new BigDecimal("100.00"))
                        .status(WalletStatus.ACTIVE)
                        .build();

        Wallet destinationWallet =
                Wallet.builder()
                        .accountId(UUID.randomUUID())
                        .currency("USD")
                        .balance(new BigDecimal("50.00"))
                        .status(WalletStatus.ACTIVE)
                        .build();

        sourceWallet =
                walletRepository.save(sourceWallet);

        destinationWallet =
                walletRepository.save(destinationWallet);

        BigDecimal amount =
                new BigDecimal("25.00");

        CreateTransferRequest transferRequest =
                new CreateTransferRequest(
                        sourceWallet.getId(),
                        destinationWallet.getId(),
                        amount
                );

        String idempotencyKey =
                UUID.randomUUID().toString();


        // =========================
        // Act
        // =========================

        mockMvc.perform(
                        post("/api/v1/transfers")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                transferRequest
                                        )
                                )
                )
                .andExpect(status().isCreated());


        // =========================
        // Assert
        // =========================

        Transfer transfer =
                transferRepository.findAll()
                        .get(0);

        OutboxEvent outboxEvent =
                outboxEventRepository.findAll()
                        .get(0);

        assertThat(outboxEvent.getAggregateType())
                .isEqualTo("TRANSFER");

        assertThat(outboxEvent.getAggregateId())
                .isEqualTo(transfer.getId());

        assertThat(outboxEvent.getEventType())
                .isEqualTo("TRANSFER_COMPLETED");

        assertThat(outboxEvent.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);

        assertThat(outboxEvent.getPayload())
                .contains(transfer.getId().toString());

        assertThat(outboxEvent.getPayload())
                .contains(sourceWallet.getId().toString());

        assertThat(outboxEvent.getPayload())
                .contains(destinationWallet.getId().toString());

        assertThat(outboxEvent.getPayload())
                .contains("25.00");

        assertThat(outboxEvent.getPayload())
                .contains("USD");
    }

    }
