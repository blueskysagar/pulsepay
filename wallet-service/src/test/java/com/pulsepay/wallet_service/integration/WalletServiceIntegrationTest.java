package com.pulsepay.wallet_service.integration;
import com.pulsepay.wallet_service.dto.request.CreateWalletRequest;
import com.pulsepay.wallet_service.dto.response.CreateWalletResponse;
import com.pulsepay.wallet_service.entity.Wallet;
import com.pulsepay.wallet_service.enums.WalletStatus;
import com.pulsepay.wallet_service.repository.*;
import com.pulsepay.wallet_service.service.cache.WalletCacheKey;
import org.junit.jupiter.api.BeforeEach;
import com.pulsepay.wallet_service.entity.OutboxEvent;
import com.pulsepay.wallet_service.dto.request.CreateTransferRequest;
import org.junit.jupiter.api.Test;
import com.pulsepay.wallet_service.entity.Transfer;
import com.pulsepay.wallet_service.enums.OutboxEventStatus;
import com.pulsepay.wallet_service.enums.TransferStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

// CORRECT Import for PostgreSQLContainer (containers package)
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;


import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.pulsepay.wallet_service.dto.request.CreditWalletRequest;
import com.pulsepay.wallet_service.dto.request.UpdateWalletStatusRequest;


@SpringBootTest //tells spring boot to start application context for this test.
@Testcontainers //tells junit this test class uses container, manage their lifecycle.
@AutoConfigureMockMvc //Its like Postman or swagger


public class WalletServiceIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");


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
    @Container
    //Use generic container docker container using the redis image specified
    // and that exposedPorts means it maps internal port which is 6379 to random available port on our computer.
    static GenericContainer<?>redis=new GenericContainer<>("redis:8.10.2")
            .withExposedPorts(6379);
    //Hey spring dont use normal redis address.Redis container that testcontainer just started ask them.
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry){
        //Ask the running redis container, what host should i use to reach you.
        registry.add(
                "spring.data.redis.host",
                redis::getHost
        );
        //Redis you told me your internal port is 6379, what host port did test container map that to you.
        registry.add(
                "spring.data.redis.port",
                () -> redis.getMappedPort(6379)
        );

    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private JsonMapper objectMapper;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private TransferRepository transferRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private TransferCompletionRecordRepository transferCompletionRecordRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @BeforeEach // Once its created we clean the data base other wise we might get duplication exception
    void cleanDatabase(){
        transferCompletionRecordRepository.deleteAll();

        processedEventRepository.deleteAll();

        outboxEventRepository.deleteAll();

        transferRepository.deleteAll();

        walletRepository.deleteAll();
    }
    @Test
    void shouldCreateWalletSuccessfully() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT ID
        // ============================================================

        /*
         * In the real system, the accountId would normally come
         * from the authentication/security layer.
         *
         * For example:
         *
         * Client
         *    ↓
         * Access Token
         *    ↓
         * Authentication / Gateway
         *    ↓
         * accountId
         *    ↓
         * Wallet Service
         *
         * Wallet Service does not need to authenticate the user
         * again for this operation.
         *
         * Therefore the controller expects:
         *
         *     @RequestAttribute("accountId")
         *
         * We simulate that authenticated identity here.
         */
        UUID accountId = UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE REQUEST
        // ============================================================

        /*
         * Create the same request object that a real API client
         * would send.
         *
         * The client wants to create a USD wallet.
         */
        CreateWalletRequest request =
                new CreateWalletRequest();

        request.setCurrency("USD");


        // ============================================================
        // ACT — SEND HTTP REQUEST
        // ============================================================

        /*
         * We deliberately call the Controller through MockMvc.
         *
         * We are NOT doing this:
         *
         *     walletService.createWallet(...)
         *
         * because that would skip the HTTP/controller layer.
         *
         * Instead the complete application flow is exercised:
         *
         *     MockMvc
         *        ↓
         *     WalletController
         *        ↓
         *     WalletService
         *        ↓
         *     WalletRepository
         *        ↓
         *     PostgreSQL Testcontainer
         */
        MvcResult result =
                mockMvc.perform(

                                /*
                                 * We use the fully-qualified MockMvc builder
                                 * because the project previously had an
                                 * ambiguous static import for post().
                                 *
                                 * This explicitly tells Java:
                                 *
                                 * "Use Spring MVC's MockMvcRequestBuilders.post()"
                                 */
                                org.springframework.test.web.servlet.request
                                        .MockMvcRequestBuilders
                                        .post("/api/v1/wallets")

                                        /*
                                         * Simulate the authenticated account.
                                         *
                                         * This creates the request attribute:
                                         *
                                         *     accountId = our generated UUID
                                         *
                                         * The controller reads it using:
                                         *
                                         *     @RequestAttribute("accountId")
                                         */
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                accountId.toString(),
                                                                null,
                                                                AuthorityUtils.NO_AUTHORITIES
                                                        )
                                                )
                                        )

                                        /*
                                         * Tell Spring that the request body
                                         * contains JSON.
                                         *
                                         * This is equivalent to the HTTP header:
                                         *
                                         *     Content-Type: application/json
                                         */
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )

                                        /*
                                         * Convert our Java request object into
                                         * JSON before sending it.
                                         *
                                         * Java:
                                         *
                                         *     request.currency = "USD"
                                         *
                                         * becomes:
                                         *
                                         *     {
                                         *         "currency": "USD"
                                         *     }
                                         */
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        request
                                                )
                                        )
                        )

                        // ====================================================
                        // ASSERT — HTTP STATUS
                        // ====================================================

                        /*
                         * Creating a resource successfully should return:
                         *
                         *     HTTP 201 Created
                         *
                         * This comes from our controller:
                         *
                         *     ResponseEntity.status(HttpStatus.CREATED)
                         */
                        .andExpect(
                                status().isCreated()
                        )

                        // ====================================================
                        // ASSERT — RESPONSE BODY
                        // ====================================================

                        /*
                         * Verify that the response contains a generated
                         * wallet ID.
                         *
                         * The client needs this ID for future operations:
                         *
                         *     GET /api/v1/wallets/{walletId}
                         *     POST /api/v1/wallets/{walletId}/credit
                         */
                        .andExpect(
                                jsonPath("$.id").exists()
                        )

                        /*
                         * Verify that the wallet belongs to the same
                         * authenticated account that created it.
                         *
                         * This is an important security/business assertion.
                         */
                        .andExpect(
                                jsonPath("$.accountId")
                                        .value(accountId.toString())
                        )

                        /*
                         * Verify that the requested currency was persisted
                         * and returned correctly.
                         */
                        .andExpect(
                                jsonPath("$.currency")
                                        .value("USD")
                        )

                        /*
                         * A newly created wallet should start with zero
                         * balance.
                         *
                         * We are testing an important business invariant:
                         *
                         * NEW WALLET → BALANCE = 0
                         */
                        .andExpect(
                                jsonPath("$.balance")
                                        .value(0)
                        )

                        /*
                         * A newly created wallet should be ACTIVE.
                         *
                         * This verifies the default business state.
                         */
                        .andExpect(
                                jsonPath("$.status")
                                        .value("ACTIVE")
                        )

                        /*
                         * andReturn() gives us the complete MVC result.
                         *
                         * We are not strictly required to use it for this
                         * test because the JSON assertions above are enough.
                         *
                         * We keep it because it follows the same pattern
                         * used in our Identity integration tests and allows
                         * us to inspect the response later if necessary.
                         */
                        .andReturn();


        // ============================================================
        // ASSERT — DATABASE STATE
        // ============================================================

        /*
         * HTTP 201 alone is not enough for an integration test.
         *
         * Imagine the controller returns 201 but the database
         * transaction actually does not persist the wallet correctly.
         *
         * The API test could still look successful.
         *
         * Therefore we verify the real PostgreSQL state.
         */
        List<Wallet> wallets =
                walletRepository.findAll();


        /*
         * Exactly one wallet should have been created.
         *
         * We started the test with an empty database.
         *
         * Therefore:
         *
         *     before → 0 wallets
         *     request → create wallet
         *     after  → 1 wallet
         */
        assertThat(wallets)
                .hasSize(1);


        /*
         * Retrieve the wallet that was persisted.
         */
        Wallet wallet =
                wallets.get(0);


        // ============================================================
        // ASSERT — ACCOUNT OWNERSHIP
        // ============================================================

        /*
         * Verify that the database wallet belongs to the account
         * that made the request.
         */
        assertThat(wallet.getAccountId())
                .isEqualTo(accountId);


        // ============================================================
        // ASSERT — CURRENCY
        // ============================================================

        /*
         * Verify that USD was actually persisted.
         */
        assertThat(wallet.getCurrency())
                .isEqualTo("USD");


        // ============================================================
        // ASSERT — INITIAL BALANCE
        // ============================================================

        /*
         * Verify the database value, not just the HTTP response.
         *
         * BigDecimal should be compared using AssertJ's
         * isEqualByComparingTo() because:
         *
         *     0
         *
         * and
         *
         *     0.0000
         *
         * can have different scales but represent the same
         * monetary value.
         */
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(
                        BigDecimal.ZERO
                );


        // ============================================================
        // ASSERT — INITIAL STATUS
        // ============================================================

        /*
         * Verify that the database state is ACTIVE.
         */
        assertThat(wallet.getStatus())
                .isEqualTo(
                        WalletStatus.ACTIVE
                );
    }
    @Test
    void shouldRejectDuplicateWalletForSameCurrency()throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT ID
        // ============================================================

        /*
         * We need an account because every wallet belongs to
         * an account.
         *
         * In the real application, this accountId would come
         * from the authentication/security layer.
         *
         * Here we simply simulate an authenticated account.
         */
        UUID accountId = UUID.randomUUID();
        // ============================================================
        // ARRANGE — CREATE FIRST WALLET REQUEST
        // ============================================================

        /*
         * The first request asks to create a USD wallet.
         *
         * The database is empty at the beginning of this test
         * because @BeforeEach deletes existing wallets.
         */
        CreateWalletRequest firstRequest =
                new CreateWalletRequest();

        firstRequest.setCurrency("USD");
        // ============================================================
        // ACT — CREATE FIRST WALLET
        // ============================================================

        /*
         * Send the request through MockMvc.
         *
         * We are NOT calling walletService directly.
         *
         * The request travels through:
         *
         * MockMvc
         *    ↓
         * Controller
         *    ↓
         * Service
         *    ↓
         * Repository
         *    ↓
         * PostgreSQL
         */
        mockMvc.perform(
                org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders
                        .post("/api/v1/wallets")

                        /*
                         * Simulate the authenticated account.
                         */
                        .with(
                                authentication(
                                        new UsernamePasswordAuthenticationToken(
                                                accountId.toString(),
                                                null,
                                                AuthorityUtils.NO_AUTHORITIES
                                        )
                                )
                        )

                        /*
                         * Tell Spring that our request body
                         * is JSON.
                         */
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )

                        /*
                         * Convert the Java request object
                         * into JSON.
                         *
                         * It becomes:
                         *
                         * {
                         *     "currency": "USD"
                         * }
                         */
                        .content(
                                objectMapper.writeValueAsString(
                                        firstRequest
                                )
                        )
        )
                // ====================================================
                // ASSERT — FIRST REQUEST SUCCEEDS
                // ====================================================

                /*
                 * There is no USD wallet yet.
                 *
                 * Therefore the first request should succeed.
                 */
                .andExpect(
                        status().isCreated()
                );
        // ============================================================
        // ARRANGE — CREATE SECOND WALLET REQUEST
        // ============================================================

        /*
         * Now we create another request.
         *
         * IMPORTANT:
         *
         * We use the SAME accountId.
         *
         * We also use the SAME currency: USD.
         *
         * Therefore this request is attempting to create
         * the exact same type of wallet again.
         */
        CreateWalletRequest secondRequest =
                new CreateWalletRequest();

        secondRequest.setCurrency("USD");


        // ============================================================
        // ACT — TRY TO CREATE DUPLICATE WALLET
        // ============================================================

        /*
         * Send the second request through the real HTTP layer.
         *
         * The application should detect that this account already
         * has a USD wallet.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post("/api/v1/wallets")

                                /*
                                 * SAME account.
                                 */
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )

                                /*
                                 * Request body is JSON.
                                 */
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )

                                /*
                                 * Convert second request to JSON.
                                 */
                                .content(
                                        objectMapper.writeValueAsString(
                                                secondRequest
                                        )
                                )
                )

                // ====================================================
                // ASSERT — DUPLICATE REQUEST IS REJECTED
                // ====================================================

                /*
                 * The account already owns a USD wallet.
                 *
                 * Therefore creating another USD wallet conflicts
                 * with the existing database state.
                 *
                 * Expected:
                 *
                 * HTTP 409 Conflict
                 */
                .andExpect(
                        status().isConflict()
                );
        // ============================================================
        // ASSERT — DATABASE STATE
        // ============================================================

        /*
         * Now verify the database.
         *
         * The first request created one wallet.
         *
         * The second request failed.
         *
         * Therefore there must still be exactly ONE wallet.
         */
        List<Wallet> wallets =
                walletRepository.findAll();

        assertThat(wallets)
                .hasSize(1);


        // ============================================================
        // ASSERT — VERIFY EXISTING WALLET
        // ============================================================

        /*
         * Get the only wallet from the database.
         */
        Wallet wallet =
                wallets.get(0);


        /*
         * Make sure the wallet belongs to our account.
         */
        assertThat(wallet.getAccountId())
                .isEqualTo(accountId);


        /*
         * Make sure the existing wallet is USD.
         */
        assertThat(wallet.getCurrency())
                .isEqualTo("USD");


        /*
         * The duplicate request must NOT change the balance.
         *
         * A newly created wallet starts with zero balance.
         */
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(
                        BigDecimal.ZERO
                );


        /*
         * The wallet should still be ACTIVE.
         */
        assertThat(wallet.getStatus())
                .isEqualTo(
                        WalletStatus.ACTIVE
                );


    }
    @Test
    void shouldCreateWalletsForDifferentCurrencies() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT ID
        // ============================================================

        /*
         * Both wallets will belong to this same account.
         */
        UUID accountId =
                UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE USD REQUEST
        // ============================================================

        CreateWalletRequest usdRequest =
                new CreateWalletRequest();

        usdRequest.setCurrency("USD");


        // ============================================================
        // ACT — CREATE USD WALLET
        // ============================================================

        /*
         * Create the first wallet for USD.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post("/api/v1/wallets")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
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
                                                usdRequest
                                        )
                                )
                )

                // First wallet should be created successfully.
                .andExpect(
                        status().isCreated()
                );


        // ============================================================
        // ARRANGE — CREATE EUR REQUEST
        // ============================================================

        /*
         * Now create another wallet request.
         *
         * IMPORTANT:
         *
         * Same account.
         * Different currency.
         *
         * This should be allowed.
         */
        CreateWalletRequest eurRequest =
                new CreateWalletRequest();

        eurRequest.setCurrency("EUR");


        // ============================================================
        // ACT — CREATE EUR WALLET
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post("/api/v1/wallets")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
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
                                                eurRequest
                                        )
                                )
                )

                // Different currency means different wallet.
                .andExpect(
                        status().isCreated()
                );


        // ============================================================
        // ASSERT — DATABASE STATE
        // ============================================================

        /*
         * We created:
         *
         *     Account A + USD
         *     Account A + EUR
         *
         * Therefore the database should contain TWO wallets.
         */
        List<Wallet> wallets =
                walletRepository.findAll();

        assertThat(wallets)
                .hasSize(2);


        // ============================================================
        // ASSERT — USD WALLET EXISTS
        // ============================================================

        assertThat(wallets)
                .anyMatch(wallet ->
                        wallet.getAccountId().equals(accountId)
                                && wallet.getCurrency().equals("USD")
                );


        // ============================================================
        // ASSERT — EUR WALLET EXISTS
        // ============================================================

        assertThat(wallets)
                .anyMatch(wallet ->
                        wallet.getAccountId().equals(accountId)
                                && wallet.getCurrency().equals("EUR")
                );
    }

    @Test
    void shouldGetWalletSuccessfully() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT ID
        // ============================================================

        UUID accountId =
                UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE WALLET REQUEST
        // ============================================================

        CreateWalletRequest request =
                new CreateWalletRequest();

        request.setCurrency("USD");


        // ============================================================
        // ARRANGE — CREATE WALLET THROUGH HTTP
        // ============================================================

        /*
         * We first create a real wallet through the API.
         *
         * We need a real walletId for the GET request.
         */
        MvcResult createResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request
                                        .MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                accountId.toString(),
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
                                                        request
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT WALLET ID
        // ============================================================

        /*
         * Read the wallet ID returned by the POST request.
         *
         * We need this ID because the GET endpoint is:
         *
         *     GET /api/v1/wallets/{walletId}
         */
        String walletId =
                objectMapper
                        .readTree(
                                createResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("id")
                        .asText();


        // ============================================================
        // ACT — GET WALLET
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .get(
                                        "/api/v1/wallets/"
                                                + walletId
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

                // ====================================================
                // ASSERT — HTTP RESPONSE
                // ====================================================

                .andExpect(
                        status().isOk()
                )

                // ====================================================
                // ASSERT — RESPONSE BODY
                // ====================================================

                .andExpect(
                        jsonPath("$.id")
                                .value(walletId)
                )

                .andExpect(
                        jsonPath("$.accountId")
                                .value(accountId.toString())
                )

                .andExpect(
                        jsonPath("$.currency")
                                .value("USD")
                )

                .andExpect(
                        jsonPath("$.balance")
                                .value(0)
                )

                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                );
    }

    @Test
    void shouldRejectAccessToWalletOwnedByAnotherAccount()
            throws Exception {

        // ============================================================
        // ARRANGE — CREATE TWO ACCOUNTS
        // ============================================================

        /*
         * Account A owns the wallet.
         */
        UUID accountA =
                UUID.randomUUID();

        /*
         * Account B will try to access Account A's wallet.
         */
        UUID accountB =
                UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE WALLET FOR ACCOUNT A
        // ============================================================

        CreateWalletRequest request =
                new CreateWalletRequest();

        request.setCurrency("USD");


        // ============================================================
        // ACT — ACCOUNT A CREATES WALLET
        // ============================================================

        MvcResult createResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request
                                        .MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                accountA.toString(),
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
                                                        request
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT ACCOUNT A'S WALLET ID
        // ============================================================

        String walletId =
                objectMapper
                        .readTree(
                                createResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("id")
                        .asText();


        // ============================================================
        // ACT — ACCOUNT B TRIES TO ACCESS ACCOUNT A'S WALLET
        // ============================================================

        /*
         * This is the actual security scenario.
         *
         * The wallet belongs to:
         *
         *     accountA
         *
         * But the request claims to be from:
         *
         *     accountB
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .get(
                                        "/api/v1/wallets/"
                                                + walletId
                                )
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountB.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                )

                // ====================================================
                // ASSERT
                // ====================================================

                /*
                 * The wallet should not be returned.
                 *
                 * Our application should treat the wallet as
                 * inaccessible to this account.
                 */
                .andExpect(
                        status().isNotFound()
                );
    }
    @Test
    void shouldGetWalletsForAuthenticatedAccount() throws Exception {

        // ============================================================
        // ARRANGE — CREATE TWO ACCOUNTS
        // ============================================================

        UUID accountA =
                UUID.randomUUID();

        UUID accountB =
                UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE USD WALLET FOR ACCOUNT A
        // ============================================================

        CreateWalletRequest usdRequest =
                new CreateWalletRequest();

        usdRequest.setCurrency("USD");


        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post("/api/v1/wallets")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountA.toString(),
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
                                                usdRequest
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                );


        // ============================================================
        // ARRANGE — CREATE EUR WALLET FOR ACCOUNT A
        // ============================================================

        CreateWalletRequest eurRequest =
                new CreateWalletRequest();

        eurRequest.setCurrency("EUR");


        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post("/api/v1/wallets")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountA.toString(),
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
                                                eurRequest
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                );


        // ============================================================
        // ARRANGE — CREATE USD WALLET FOR ACCOUNT B
        // ============================================================

        /*
         * This wallet should NOT appear when Account A lists wallets.
         */
        CreateWalletRequest accountBRequest =
                new CreateWalletRequest();

        accountBRequest.setCurrency("USD");


        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post("/api/v1/wallets")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountB.toString(),
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
                                                accountBRequest
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                );


        // ============================================================
        // ACT — ACCOUNT A LISTS WALLETS
        // ============================================================

        /*
         * Account A owns:
         *
         *     USD
         *     EUR
         *
         * Account B owns:
         *
         *     USD
         *
         * Account A should only receive TWO wallets.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .get("/api/v1/wallets")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountA.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        )
                                )
                )

                // ====================================================
                // ASSERT — HTTP STATUS
                // ====================================================

                .andExpect(
                        status().isOk()
                )

                // ====================================================
                // ASSERT — PAGE SIZE
                // ====================================================

                /*
                 * WalletPageResponse should contain the wallet list.
                 *
                 * If your response property is called "wallets",
                 * this checks that two wallets were returned.
                 */
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(2)
                );
    }
    @Test
    void shouldCreditWalletSuccessfully() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        UUID accountId =
                UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE WALLET
        // ============================================================

        CreateWalletRequest createRequest =
                new CreateWalletRequest();

        createRequest.setCurrency("USD");


        MvcResult createResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request
                                        .MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                accountId.toString(),
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
                                                        createRequest
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT WALLET ID
        // ============================================================

        String walletId =
                objectMapper
                        .readTree(
                                createResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("id")
                        .asText();


        // ============================================================
        // ARRANGE — CREATE CREDIT REQUEST
        // ============================================================

        CreditWalletRequest creditRequest =
                new CreditWalletRequest();

        creditRequest.setAmount(
                new BigDecimal("100.00")
        );


        // ============================================================
        // ACT — CREDIT WALLET
        // ============================================================

        /*
         * Idempotency-Key uniquely identifies this operation.
         *
         * If the client retries this exact request later,
         * the server can recognize it as the same operation.
         */
        String idempotencyKey =
                UUID.randomUUID().toString();


        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/"
                                                + walletId
                                                + "/credit"
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
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
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

                // ====================================================
                // ASSERT — HTTP RESPONSE
                // ====================================================

                .andExpect(
                        status().isOk()
                )

                // ====================================================
                // ASSERT — RESPONSE BODY
                // ====================================================

                .andExpect(
                        jsonPath("$.id")
                                .value(walletId)
                )

                .andExpect(
                        jsonPath("$.accountId")
                                .value(accountId.toString())
                )

                .andExpect(
                        jsonPath("$.currency")
                                .value("USD")
                )

                /*
                 * Initial balance = 0
                 *
                 * Credit = 100
                 *
                 * Final balance = 100
                 */
                .andExpect(
                        jsonPath("$.balance")
                                .value(100)
                )

                .andExpect(
                        jsonPath("$.status")
                                .value("ACTIVE")
                );


        // ============================================================
        // ASSERT — DATABASE STATE
        // ============================================================

        Wallet wallet =
                walletRepository
                        .findByIdAndAccountId(
                                UUID.fromString(walletId),
                                accountId
                        )
                        .orElseThrow();


        /*
         * Verify that the real database balance is 100.
         */
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(
                        new BigDecimal("100.00")
                );
    }
    @Test
    void shouldNotCreditWalletTwiceWhenSameIdempotencyKeyIsReused()
            throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        UUID accountId =
                UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE WALLET
        // ============================================================

        CreateWalletRequest createRequest =
                new CreateWalletRequest();

        createRequest.setCurrency("USD");


        MvcResult createResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request
                                        .MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                accountId.toString(),
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
                                                        createRequest
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT WALLET ID
        // ============================================================

        String walletId =
                objectMapper
                        .readTree(
                                createResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("id")
                        .asText();


        // ============================================================
        // ARRANGE — CREATE CREDIT REQUEST
        // ============================================================

        CreditWalletRequest creditRequest =
                new CreditWalletRequest();

        creditRequest.setAmount(
                new BigDecimal("100.00")
        );


        // ============================================================
        // ARRANGE — CREATE IDEMPOTENCY KEY
        // ============================================================

        /*
         * Both requests will intentionally use the SAME key.
         */
        String idempotencyKey =
                UUID.randomUUID().toString();


        // ============================================================
        // ACT — FIRST CREDIT REQUEST
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/"
                                                + walletId
                                                + "/credit"
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
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
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
                .andExpect(
                        status().isOk()
                );


        // ============================================================
        // ACT — SECOND CREDIT REQUEST
        // ============================================================

        /*
         * This simulates a client retry.
         *
         * Same:
         *
         *     account
         *     wallet
         *     amount
         *     idempotency key
         *
         * Therefore the server should recognize that this operation
         * has already completed.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/"
                                                + walletId
                                                + "/credit"
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
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
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

                /*
                 * The replay should still succeed.
                 *
                 * We return the existing result rather than performing
                 * the credit operation again.
                 */
                .andExpect(
                        status().isOk()
                )

                .andExpect(
                        jsonPath("$.balance")
                                .value(100)
                );


        // ============================================================
        // ASSERT — DATABASE BALANCE
        // ============================================================

        Wallet wallet =
                walletRepository
                        .findByIdAndAccountId(
                                UUID.fromString(walletId),
                                accountId
                        )
                        .orElseThrow();


        /*
         * MOST IMPORTANT ASSERTION:
         *
         * The wallet must contain 100, NOT 200.
         */
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(
                        new BigDecimal("100.00")
                );
    }


    @Test
    void shouldRejectIdempotencyKeyReuseWithDifferentRequest()
            throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        UUID accountId =
                UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE WALLET
        // ============================================================

        CreateWalletRequest createRequest =
                new CreateWalletRequest();

        createRequest.setCurrency("USD");


        MvcResult createResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request
                                        .MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                accountId.toString(),
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
                                                        createRequest
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn();


        String walletId =
                objectMapper
                        .readTree(
                                createResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("id")
                        .asText();


        // ============================================================
        // ARRANGE — FIRST CREDIT
        // ============================================================

        CreditWalletRequest firstCredit =
                new CreditWalletRequest();

        firstCredit.setAmount(
                new BigDecimal("100.00")
        );


        String idempotencyKey =
                UUID.randomUUID().toString();


        // ============================================================
        // ACT — FIRST REQUEST
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/"
                                                + walletId
                                                + "/credit"
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
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                firstCredit
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                );


        // ============================================================
        // ARRANGE — DIFFERENT CREDIT REQUEST
        // ============================================================

        /*
         * Same idempotency key.
         *
         * BUT different amount.
         *
         * First request:
         *
         *     amount = 100
         *
         * Second request:
         *
         *     amount = 200
         *
         * Therefore the fingerprint will be different.
         */
        CreditWalletRequest secondCredit =
                new CreditWalletRequest();

        secondCredit.setAmount(
                new BigDecimal("200.00")
        );


        // ============================================================
        // ACT — REUSE KEY WITH DIFFERENT REQUEST
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/"
                                                + walletId
                                                + "/credit"
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
                                .header(
                                        "Idempotency-Key",
                                        idempotencyKey
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                secondCredit
                                        )
                                )
                )

                // ====================================================
                // ASSERT
                // ====================================================

                /*
                 * Same key + different request is a conflict.
                 */
                .andExpect(
                        status().isConflict()
                );


        // ============================================================
        // ASSERT — BALANCE MUST REMAIN 100
        // ============================================================

        Wallet wallet =
                walletRepository
                        .findByIdAndAccountId(
                                UUID.fromString(walletId),
                                accountId
                        )
                        .orElseThrow();

        assertThat(wallet.getBalance())
                .isEqualByComparingTo(
                        new BigDecimal("100.00")
                );
    }

    @Test
    void shouldRejectCreditWhenWalletBelongsToAnotherAccount()
            throws Exception {

        // ============================================================
        // ARRANGE — TWO ACCOUNTS
        // ============================================================

        UUID accountA =
                UUID.randomUUID();

        UUID accountB =
                UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE WALLET FOR ACCOUNT A
        // ============================================================

        CreateWalletRequest createRequest =
                new CreateWalletRequest();

        createRequest.setCurrency("USD");


        MvcResult createResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request
                                        .MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                accountA.toString(),
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
                                                        createRequest
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT WALLET ID
        // ============================================================

        String walletId =
                objectMapper
                        .readTree(
                                createResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("id")
                        .asText();


        // ============================================================
        // ARRANGE — CREDIT REQUEST
        // ============================================================

        CreditWalletRequest creditRequest =
                new CreditWalletRequest();

        creditRequest.setAmount(
                new BigDecimal("100.00")
        );


        // ============================================================
        // ACT — ACCOUNT B TRIES TO CREDIT ACCOUNT A'S WALLET
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/"
                                                + walletId
                                                + "/credit"
                                )

                                /*
                                 * IMPORTANT:
                                 *
                                 * The wallet belongs to Account A,
                                 * but the authenticated account is B.
                                 */
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountB.toString(),
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

                // ====================================================
                // ASSERT
                // ====================================================

                /*
                 * Wallet is not accessible to Account B.
                 */
                .andExpect(
                        status().isNotFound()
                );


        // ============================================================
        // ASSERT — DATABASE MUST NOT CHANGE
        // ============================================================

        Wallet wallet =
                walletRepository
                        .findById(
                                UUID.fromString(walletId)
                        )
                        .orElseThrow();


        /*
         * The wallet originally had zero balance.
         *
         * Account B must not have been able to credit it.
         */
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(
                        BigDecimal.ZERO
                );
    }
    @Test
    void shouldRejectCreditWhenWalletIsSuspended()
            throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        UUID accountId =
                UUID.randomUUID();


        // ============================================================
        // ARRANGE — CREATE WALLET
        // ============================================================

        CreateWalletRequest createRequest =
                new CreateWalletRequest();

        createRequest.setCurrency("USD");


        MvcResult createResult =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request
                                        .MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(
                                                authentication(
                                                        new UsernamePasswordAuthenticationToken(
                                                                accountId.toString(),
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
                                                        createRequest
                                                )
                                        )
                        )
                        .andExpect(
                                status().isCreated()
                        )
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT WALLET ID
        // ============================================================

        String walletId =
                objectMapper
                        .readTree(
                                createResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("id")
                        .asText();


        // ============================================================
        // ARRANGE — SUSPEND WALLET
        // ============================================================

        UpdateWalletStatusRequest statusRequest =
                new UpdateWalletStatusRequest();

        statusRequest.setStatus(
                WalletStatus.SUSPENDED
        );


        /*
         * Change wallet status through the real API.
         */
        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .patch(
                                        "/api/v1/wallets/"
                                                + walletId
                                                + "/status"
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
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                statusRequest
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                );


        // ============================================================
        // ARRANGE — CREDIT REQUEST
        // ============================================================

        CreditWalletRequest creditRequest =
                new CreditWalletRequest();

        creditRequest.setAmount(
                new BigDecimal("100.00")
        );


        // ============================================================
        // ACT — TRY TO CREDIT SUSPENDED WALLET
        // ============================================================

        mockMvc.perform(
                        org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders
                                .post(
                                        "/api/v1/wallets/"
                                                + walletId
                                                + "/credit"
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

                // ====================================================
                // ASSERT
                // ====================================================

                /*
                 * The wallet is suspended.
                 *
                 * Therefore credit must be rejected.
                 */
                .andExpect(
                        status().isConflict()
                );


        // ============================================================
        // ASSERT — BALANCE REMAINS ZERO
        // ============================================================

        Wallet wallet =
                walletRepository
                        .findById(
                                UUID.fromString(walletId)
                        )
                        .orElseThrow();

        assertThat(wallet.getBalance())
                .isEqualByComparingTo(
                        BigDecimal.ZERO
                );
    }
    @Test
    void shouldPopulateRedisCacheAfterWalletRead() throws Exception{
        //Arrange Create account
        UUID accountId = UUID.randomUUID();
        //Arrange create wallet
        CreateWalletRequest request = new CreateWalletRequest();
        request.setCurrency("USD");
        //Act -create wallet
        String response =
                mockMvc.perform(
                        MockMvcRequestBuilders
                                .post("/api/v1/wallets")
                                .with(
                                        authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES

                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))

                ).andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        // ============================================================
        // ARRANGE — GET WALLET ID
        // ============================================================

        CreateWalletResponse createdWallet =
                objectMapper.readValue(
                        response,
                        CreateWalletResponse.class
                );

        UUID walletId =
                createdWallet.getId();


        // ============================================================
        // ACT — GET WALLET
        // ============================================================

        mockMvc.perform(
                        MockMvcRequestBuilders
                                .get("/api/v1/wallets/{walletId}", walletId)
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
                .andExpect(
                        status().isOk()
                );
//Assert redis cache
        //After I perform get from real API go directly to the redis container and check whether the wallet was cached.
        String cacheKey =
                WalletCacheKey.forWallet(
                        accountId,
                        walletId
                );
        String cachedValue =
                redisTemplate
                        .opsForValue()
                        .get(cacheKey);
        assertThat(cachedValue).isNotNull();


    }
    @Test
    void shouldReturnWalletFromRedisCacheWhenCacheHit() throws Exception {

        // Arrange
        UUID accountId = UUID.randomUUID();

        CreateWalletRequest request = new CreateWalletRequest();
        request.setCurrency("USD");

        String response =
                mockMvc.perform(
                                MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        ))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request))
                        )
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        CreateWalletResponse createdWallet =
                objectMapper.readValue(
                        response,
                        CreateWalletResponse.class
                );

        UUID walletId = createdWallet.getId();

        // Put a known wallet representation directly into Redis.
        // The balance is deliberately different from the database value.
        CreateWalletResponse cachedWallet = new CreateWalletResponse();

        cachedWallet.setId(walletId);
        cachedWallet.setAccountId(accountId);
        cachedWallet.setCurrency("USD");
        cachedWallet.setBalance(new BigDecimal("750.00"));
        cachedWallet.setStatus(WalletStatus.ACTIVE);

        String cacheKey =
                WalletCacheKey.forWallet(accountId, walletId);

        String cachedValue =
                objectMapper.writeValueAsString(cachedWallet);

        redisTemplate.opsForValue().set(
                cacheKey,
                cachedValue,
                Duration.ofSeconds(60)
        );

        // Act
        mockMvc.perform(
                MockMvcRequestBuilders
                        .get("/api/v1/wallets/{walletId}", walletId)
                        .with(authentication(
                                        new UsernamePasswordAuthenticationToken(
                                                accountId.toString(),
                                                null,
                                                AuthorityUtils.NO_AUTHORITIES
                                        )
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                        // Assert
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.balance").value(750.00))
                        .andExpect(jsonPath("$.currency").value("USD"));
    }
    @Test
    void shouldEvictWalletCacheAfterCredit() throws Exception {

        // Arrange
        UUID accountId = UUID.randomUUID();

        CreateWalletRequest request = new CreateWalletRequest();
        request.setCurrency("USD");

        String response =
                mockMvc.perform(
                                MockMvcRequestBuilders
                                        .post("/api/v1/wallets")
                                        .with(authentication(
                                                new UsernamePasswordAuthenticationToken(
                                                        accountId.toString(),
                                                        null,
                                                        AuthorityUtils.NO_AUTHORITIES
                                                )
                                        ))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request))
                        )
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        CreateWalletResponse createdWallet =
                objectMapper.readValue(
                        response,
                        CreateWalletResponse.class
                );

        UUID walletId = createdWallet.getId();

        String cacheKey =
                WalletCacheKey.forWallet(accountId, walletId);

        // Put the wallet into Redis first.
        redisTemplate.opsForValue().set(
                cacheKey,
                objectMapper.writeValueAsString(createdWallet),
                Duration.ofSeconds(60)
        );

        // Confirm that the cache actually exists before the mutation.
        assertThat(
                redisTemplate.opsForValue().get(cacheKey)
        ).isNotNull();

        // Act
        mockMvc.perform(
                        MockMvcRequestBuilders
                                .post("/api/v1/wallets/{walletId}/credit", walletId)
                                .with(authentication(
                                        new UsernamePasswordAuthenticationToken(
                                                accountId.toString(),
                                                null,
                                                AuthorityUtils.NO_AUTHORITIES
                                        )
                                ))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                            {
                                "amount": 100.00
                            }
                            """)
                )
                // Assert
                .andExpect(status().isOk());

        // The wallet changed, so the old Redis representation must be gone.
        assertThat(
                redisTemplate.opsForValue().get(cacheKey)
        ).isNull();
    }










}
