package com.pulsepay.identity_service.integration;
import com.pulsepay.identity_service.dto.request.RefreshTokenRequest;
import com.pulsepay.identity_service.entity.RefreshSession;
import com.pulsepay.identity_service.repository.RefreshSessionRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulsepay.identity_service.dto.request.LoginRequest;
import com.pulsepay.identity_service.dto.request.RegisterAccountRequest;
import com.pulsepay.identity_service.entity.IdentityAccount;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import com.pulsepay.identity_service.enums.AccountStatus;
import com.pulsepay.identity_service.repository.IdentityAccountRepository;
import com.pulsepay.identity_service.security.token.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
        import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest // Start SpringBoot application context for this test
// application context is nothing but like registry which has record of all configurations and beans and it manages them.
//Tell Junit to manage the lifecycle of the container in this test class, manage means start before the test and clean after the test
@Testcontainers
@AutoConfigureMockMvc //Configure mockMvc so that i can simulate HTTp requests
public class IdentityAccountIntegrationTest {
    @Container  // this tells which container to manage.
    //For my integration test give me actual Postgresql 17 database.
    @ServiceConnection
//Without this we have to manually supply springboot this is username this is password this is portnumber.
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IdentityAccountRepository identityAccountRepository;
    @Autowired
    private JsonMapper objectMapper; // Jackson's class whose job is to map java entity to json or convert back to entity.
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RefreshSessionRepository refreshSessionRepository;
    @Autowired
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void cleanDatabase() {
        // Refresh sessions reference identity accounts.
        // Therefore, delete the child records first.
        refreshSessionRepository.deleteAll();

        // Now it is safe to delete the parent accounts.
        identityAccountRepository.deleteAll();
    }

    @Test
    void shouldRegisterAccountSuccessfully() throws Exception {
        //Arrange (means what things I need to perform this test operations
        RegisterAccountRequest request = new RegisterAccountRequest();
        request.setName("John Doe");
        request.setEmail("John.Doe@Example.com");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");
        //First convert JsonDTo request to JSON
        String requestJson = objectMapper.writeValueAsString(request);
        //Act means what endpoints are we hitting like  post or get etc.
        mockMvc.perform(
                        post("/api/v1/accounts")
                                //Tell spring request body is json
                                .contentType(MediaType.APPLICATION_JSON)
                                //Put your Json back into request body
                                .content(requestJson)
                ) //Assert- HttpResponse Verification
                .andDo(result -> {

                    Exception exception = result.getResolvedException();

                    if (exception != null) {
                        System.out.println("========== RESOLVED EXCEPTION ==========");
                        exception.printStackTrace();

                        Throwable cause = exception.getCause();

                        while (cause != null) {
                            System.out.println("========== CAUSE ==========");
                            cause.printStackTrace();
                            cause = cause.getCause();
                        }

                        System.out.println("========================================");
                    }

                })
                .andDo(print())

                .andExpect(status().isCreated())
                //Verify that the response contains the expectedValues
                .andExpect(jsonPath("$.name").value("John Doe"))
                /*
                 * The request contained:
                 *
                 * John.Doe@Example.com
                 *
                 * The response should contain the normalized form.
                 */
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.phone").value("+9779812345678"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));


        //Verification means database verification
        // We are not mocking around IdentityAccountRepository
        //We are talking to Postgresql container
        // Now we verify the actual state inside PostgreSQL.
        //
        // This is important because an HTTP 201 alone does not
        // prove that the correct data was persisted.

        assertEquals(1,
                identityAccountRepository.count()

        );

        // Now lets retrieve the account that was persisted or created by our test
        IdentityAccount savedAccount = identityAccountRepository.findAll().get(0);
        //Assert -ID
        assertNotNull(savedAccount.getId());
        //Assert-Name
        assertEquals(
                "John Doe",
                savedAccount.getName()
        );

        // ============================================================
        // ASSERT — EMAIL NORMALIZATION
        // ============================================================
        //
        // Request:
        //     John.Doe@Example.com
        //
        // Expected persisted value:
        //     john.doe@example.com
        //
        // This proves our normalization logic was actually executed.
        assertEquals(
                "john.doe@example.com",
                savedAccount.getEmail()
        );

        // ============================================================
        // ASSERT — PHONE NORMALIZATION
        // ============================================================
        //
        // The phone number should be persisted in E.164 format.
        assertEquals(
                "+9779812345678",
                savedAccount.getPhone()
        );
        // ============================================================
        // ASSERT — ACCOUNT STATUS
        // ============================================================
        //
        // New accounts should begin in our defined initial state.
        assertEquals(
                AccountStatus.ACTIVE,
                savedAccount.getStatus()
        );
        // ============================================================
        // ASSERT — PASSWORD SECURITY
        // ============================================================
        //
        // The raw password must NEVER be stored in the database.
        assertNotNull(
                savedAccount.getPasswordHash()
        );
        assertNotEquals(request.getPassword(), savedAccount.getPasswordHash());
        // Also the raw password is already hashed using argonpasswordconfig and is stored in potgres now it take the raw password again hashit and then match if it matches with the one alredy saved in postgresql
        assertTrue(
                passwordEncoder.matches(
                        request.getPassword(), savedAccount.getPasswordHash()
                )
        );
        //Base entity auditing fields should not be empty
        assertNotNull(savedAccount.getCreatedAt());
        assertNotNull(savedAccount.getUpdatedAt());


    }

    @Test
    void shouldRejectDuplicationEmail() throws Exception {
        // ============================================================
        // ARRANGE
        // ============================================================
        // First, create a valid account that will already exist
        // in the database.

        RegisterAccountRequest existingAccount = new RegisterAccountRequest();
        existingAccount.setName("John Doe");
        existingAccount.setEmail("John.Doe@Example.com");
        existingAccount.setPhone("+9779812345678");
        existingAccount.setPassword("StrongPassword123");
        // Convert the Java request object into JSON because
        // our API receives JSON in the HTTP request body.
        String existingAccountJson = objectMapper.writeValueAsString(existingAccount);
        // Send the first registration request.
        // This creates the account that will cause the duplicate
        // email scenario later.
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(existingAccountJson))
                .andExpect(status().isCreated());
        // ============================================================
        // ARRANGE - DUPLICATE REQUEST
        // ============================================================
        // Now create another registration request.
        //
        // The email is intentionally the SAME.
        // The phone number is DIFFERENT.
        //
        // This proves that our duplicate check is specifically
        // enforcing email uniqueness.
        RegisterAccountRequest duplicateEmailRequest = new RegisterAccountRequest();

        duplicateEmailRequest.setName("Jane Doe");
        duplicateEmailRequest.setEmail("john.doe@example.com");
        duplicateEmailRequest.setPhone("+9779800000000");
        duplicateEmailRequest.setPassword("AnotherStrongPassword123");

        String duplicateEmailJson = objectMapper.writeValueAsString(duplicateEmailRequest);
        // ============================================================
        // ACT + ASSERT
        // ============================================================
        // Attempt to register the second account.
        //
        // Because the email already exists, the API should reject
        // the request with HTTP 409 CONFLICT.
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateEmailJson))

                .andExpect(status().isConflict())

                // Verify that our standardized error response
                // contains the expected business error message.
                .andExpect(jsonPath("$.message")
                        .value("An account already exists with this email."));
        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // Only the original account should exist.
        //
        // The failed duplicate registration must NOT create
        // another database record.

        assertEquals(1, identityAccountRepository.count());
    }

    @Test
    void shouldRejectDuplicatePhone() throws Exception {
        // ============================================================
        // ARRANGE
        // ============================================================
        // First, create a valid account that will already exist
        // in the database.

        RegisterAccountRequest existingAccount = new RegisterAccountRequest();

        existingAccount.setName("John Doe");
        existingAccount.setEmail("john@example.com");
        existingAccount.setPhone("+9779812345678");
        existingAccount.setPassword("StrongPassword123");
        String existingAccountJson =
                objectMapper.writeValueAsString(existingAccount);

        // Send the first registration request.
        // This creates the existing account in PostgreSQL.
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(existingAccountJson)
                )
                .andExpect(status().isCreated());
        // ============================================================
        // ARRANGE — DUPLICATE PHONE REQUEST
        // ============================================================
        // Create a second registration request.
        //
        // The phone number is intentionally the SAME.
        // The email is DIFFERENT.
        //
        // This isolates the phone uniqueness rule.
        RegisterAccountRequest duplicatePhoneRequest =
                new RegisterAccountRequest();

        duplicatePhoneRequest.setName("Jane Doe");
        duplicatePhoneRequest.setEmail("jane@example.com");
        duplicatePhoneRequest.setPhone("+9779812345678");
        duplicatePhoneRequest.setPassword("AnotherStrongPassword123");
        String duplicatePhoneJson =
                objectMapper.writeValueAsString(duplicatePhoneRequest);


        // ============================================================
        // ACT + ASSERT
        // ============================================================
        // Attempt to register the second account.
        //
        // Because the phone number already exists,
        // the API should reject the request.
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(duplicatePhoneJson)
                )
                .andExpect(status().isConflict())

                // Verify our standardized business error message.

                .andExpect(jsonPath("$.errorCode")
                        .value("ACCOUNT_ALREADY_EXISTS"))

                // Verify the business-specific error message.
                .andExpect(jsonPath("$.message")
                        .value("An account already exists with this phone number."))

                // Verify that the response contains a timestamp.
                .andExpect(jsonPath("$.timestamp")
                        .exists())

                // Verify that the error identifies the endpoint
                // that produced the failure.
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));
        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // The failed registration must NOT create
        // a second account.

        assertEquals(
                1,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectRequestWhenRequiredFieldsAreMissing() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // We intentionally create an invalid request.
        //
        // All four required fields are missing.
        //
        // This test verifies that Bean Validation rejects the request
        // before the service attempts to create an account.

        RegisterAccountRequest request = new RegisterAccountRequest();

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT
        // ============================================================
        // Send the invalid request to the real controller.
        //
        // MockMvc -> Controller -> @Valid -> Validation
        //
        // The request should be rejected by validation.

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )

                // ====================================================
                // ASSERT — HTTP RESPONSE
                // ====================================================
                // Validation failure should return HTTP 400.
                .andExpect(status().isBadRequest())
                // Verify our standardized validation error code.
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_ERROR"))

                // Because all fields are invalid, we only verify
                // that the validation message exists.
                .andExpect(jsonPath("$.message")
                        .exists())

                // Verify timestamp exists.
                .andExpect(jsonPath("$.timestamp")
                        .exists())

                // Verify the endpoint path.
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

    }

    @Test
    void shouldRejectInvalidEmail() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Create a request where all required fields are valid,
        // except the email address.
        //
        // This isolates the @Email validation rule.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john.doe"); // Invalid email
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================
        // Send the request to the real registration endpoint.
        //
        // Bean Validation should detect that the email is invalid
        // and reject the request with HTTP 400.

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )

                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // The request failed validation before reaching the service.
        //
        // Therefore, no account should have been persisted.

        assertEquals(
                0,
                identityAccountRepository.count()
        );

    }

    @Test
    void shouldRejectBlankName() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Everything is valid except the name.
        //
        // This isolates the @NotBlank validation rule on name.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("");
        request.setEmail("john@example.com");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================
        // @NotBlank on the name field should reject the request.

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                // Standardized validation error.
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_ERROR"))

                // Exact validation message generated by Bean Validation.
                .andExpect(jsonPath("$.message")
                        .value("name: Name is Required"))

                // Error metadata.
                .andExpect(jsonPath("$.timestamp")
                        .exists())

                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // Validation failed before account creation,
        // therefore nothing should have been persisted.

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectBlankEmail() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Everything is valid except the email.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("email: Email is Required"))
                .andExpect(jsonPath("$.timestamp")
                        .exists())
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectBlankPhone() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Everything is valid except the phone number.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("phone: Phone is Required"))
                .andExpect(jsonPath("$.timestamp")
                        .exists())
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectBlankPassword() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Everything is valid except the password.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("+9779812345678");
        request.setPassword("");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .exists())
                .andExpect(jsonPath("$.timestamp")
                        .exists())
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));


        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectNameExceedingMaximumLength() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // The name must not exceed 100 characters.
        // We intentionally provide 101 characters.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("A".repeat(101));
        request.setEmail("john@example.com");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================
        // @Size(max = 100) should reject the request.

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // Validation happens before the service creates the account.

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectEmailExceedingMaximumLength() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Email is allowed to contain at most 255 characters.
        // We intentionally provide 256 characters.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("a".repeat(244) + "@example.com");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectPhoneExceedingMaximumLength() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Phone number must not exceed 30 characters.
        // We intentionally provide 31 characters.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("1".repeat(31));
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectPasswordShorterThanMinimumLength() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Password must contain at least 12 characters.
        // We intentionally provide only 11 characters.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("+9779812345678");
        request.setPassword("A".repeat(11));

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectPasswordExceedingMaximumLength() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Password must not exceed 72 characters.
        // We intentionally provide 73 characters.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("+9779812345678");
        request.setPassword("A".repeat(73));

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectInvalidEmailFormat() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // The email field is required to contain a valid email format.
        // @Email on RegisterAccountRequest enforces this rule.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("not-an-email");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================
        // The request should be rejected before reaching
        // IdentityAccountService.

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // Invalid input must never create an account.

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectMissingName() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Name is mandatory.
        // We intentionally do not provide a name.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setEmail("john@example.com");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectMissingEmail() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Email is mandatory.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectMissingPhone() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectMissingPassword() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("+9779812345678");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectRequestWithMultipleValidationErrors() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Several fields are intentionally invalid:
        //
        // name     -> blank
        // email    -> invalid format
        // phone    -> blank
        // password -> too short
        //
        // This verifies that our validation layer handles
        // multiple violations in the same request.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("");
        request.setEmail("not-an-email");
        request.setPhone("");
        request.setPassword("short");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================
        // The controller should reject the request during
        // @Valid validation.
        //
        // IdentityAccountService should never be called.

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // No account should have been created because
        // validation failed before business processing.

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectWhitespaceOnlyPhone() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("   ");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("phone: Phone is Required"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectWhitespaceOnlyName() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // The name contains only whitespace.
        //
        // @NotBlank should reject this value because whitespace
        // does not represent a meaningful name.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("     ");
        request.setEmail("john@example.com");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("name: Name is Required"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // Validation should happen before the service is executed.
        // Therefore no account should be persisted.

        assertEquals(
                0,
                identityAccountRepository.count()
        );

    }

    @Test
    void shouldRejectWhitespaceOnlyEmail() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("     ");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .exists())
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectWhitespaceOnlyPassword() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("+9779812345678");
        request.setPassword("            ");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectInvalidPhoneNumber() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // The phone passes @NotBlank and @Size validation,
        // but it is not a real valid phone number according
        // to libphonenumber.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("+977123");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                // Invalid phone is a client-side input problem.
                .andExpect(status().isBadRequest())

                // Verify our business validation error code.
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_PHONE_NUMBER"))

                // Verify the exception message.
                .andExpect(jsonPath("$.message")
                        .value("Invalid phone number"))

                // Verify standard error metadata.
                .andExpect(jsonPath("$.timestamp")
                        .exists())

                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // Invalid phone number must never create an account.

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectUnparseablePhoneNumber() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("abc-not-a-phone");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_PHONE_NUMBER"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid phone number"))
                .andExpect(jsonPath("$.timestamp")
                        .exists())
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

        // Nothing should reach the database.
        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectUnassignedPhoneNumber() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // This value looks like a phone number, but it should not
        // be accepted as a valid international phone number.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("+977123456789");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldReturnStandardizedErrorResponseForDuplicateEmail() throws Exception {

        // ============================================================
        // ARRANGE — CREATE EXISTING ACCOUNT
        // ============================================================

        RegisterAccountRequest existingAccount = new RegisterAccountRequest();

        existingAccount.setName("John Doe");
        existingAccount.setEmail("john@example.com");
        existingAccount.setPhone("+9779812345678");
        existingAccount.setPassword("StrongPassword123");

        String existingAccountJson =
                objectMapper.writeValueAsString(existingAccount);

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(existingAccountJson)
                )
                .andExpect(status().isCreated());

        // ============================================================
        // ARRANGE — DUPLICATE REQUEST
        // ============================================================

        RegisterAccountRequest duplicateRequest =
                new RegisterAccountRequest();

        duplicateRequest.setName("Jane Doe");
        duplicateRequest.setEmail("john@example.com");
        duplicateRequest.setPhone("+9779800000000");
        duplicateRequest.setPassword("AnotherStrongPassword123");

        String duplicateRequestJson =
                objectMapper.writeValueAsString(duplicateRequest);

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(duplicateRequestJson)
                )
                .andExpect(status().isConflict())

                // Verify standardized error code.
                .andExpect(jsonPath("$.errorCode")
                        .value("ACCOUNT_ALREADY_EXISTS"))

                // Verify business error message.
                .andExpect(jsonPath("$.message")
                        .value("An account already exists with this email."))

                // Verify timestamp exists.
                .andExpect(jsonPath("$.timestamp")
                        .exists())

                // Verify request path.
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/accounts"));

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // Duplicate registration must not create another account.

        assertEquals(
                1,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // This is intentionally malformed JSON.
        // The closing quote/bracket is missing.

        String malformedJson = """
                {
                    "name": "John Doe",
                    "email": "john@example.com",
                    "phone": "+9779812345678",
                    "password": "StrongPassword123"
                """;

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(malformedJson)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================
        // Malformed JSON must never reach the service or persistence.

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectUnsupportedContentType() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        String requestBody = "This is not JSON";

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(requestBody)
                )
                .andExpect(status().isUnsupportedMediaType());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldRejectMissingRequestBody() throws Exception {

        // ============================================================
        // ACT + ASSERT
        // ============================================================
        // No request body is supplied at all.

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest());

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                identityAccountRepository.count()
        );
    }

    @Test
    void shouldNormalizeEmailBeforePersistence() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================
        // Intentionally provide leading/trailing whitespace
        // and uppercase characters.

        RegisterAccountRequest request = new RegisterAccountRequest();

        request.setName("John Doe");
        request.setEmail("John.Doe@Example.COM");
        request.setPhone("+9779812345678");
        request.setPassword("StrongPassword123");

        String requestJson =
                objectMapper.writeValueAsString(request);

        // ============================================================
        // ACT + ASSERT — HTTP RESPONSE
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isCreated())

                // The response should contain the canonical email.
                .andExpect(jsonPath("$.email")
                        .value("john.doe@example.com"));

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        IdentityAccount savedAccount =
                identityAccountRepository.findAll().get(0);

        assertEquals(
                "john.doe@example.com",
                savedAccount.getEmail()
        );
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        //Arrange
        RegisterAccountRequest registerAccountRequest = new RegisterAccountRequest();
        registerAccountRequest.setName("John Doe");
        registerAccountRequest.setEmail("john@example.com");
        registerAccountRequest.setPhone("+9779812345678");
        registerAccountRequest.setPassword("StrongPassword123");
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerAccountRequest))
                )
                .andExpect(status().isCreated());
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessTokenExpiresIn").exists());
        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                1,
                refreshSessionRepository.count()
        );


    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        RegisterAccountRequest registerRequest =
                new RegisterAccountRequest();

        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPhone("+9779812345678");
        registerRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerRequest))
                )
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("WrongPassword123");

        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));

        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        assertEquals(
                0,
                refreshSessionRepository.count()
        );
    }

    @Test
    void shouldRejectLoginWithUnknownEmail() throws Exception {

        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setEmail("unknown@example.com");
        loginRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));

        assertEquals(
                0,
                refreshSessionRepository.count()
        );
    }

    @Test
    void shouldRefreshSuccessfully() throws Exception {
        //Arrange
        RegisterAccountRequest registerAccountRequest = new RegisterAccountRequest();
        registerAccountRequest.setName("John Doe");
        registerAccountRequest.setEmail("john@example.com");
        registerAccountRequest.setPhone("+9779812345678");
        registerAccountRequest.setPassword("StrongPassword123");
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerAccountRequest))

                )
                .andExpect(status().isCreated());
        // ============================================================
        // ARRANGE - Login
        // ============================================================
        // We need a real refresh token.
        // The application creates it during successful login.

        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");
        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest))

                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessTokenExpiresIn").exists())
                .andReturn();
        // ============================================================
        // ARRANGE - Extract refresh token
        // ============================================================
        // The login response contains the refresh token.
        // We extract it because we are going to send it to
        // the refresh endpoint.
        //Converting json into java object
        String refreshToken = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken")
                .asText();
        // ============================================================
        // ACT
        // ============================================================
        // Send the refresh token to the refresh endpoint.
        RefreshTokenRequest refreshRequest =
                new RefreshTokenRequest();

        refreshRequest.setRefreshToken(refreshToken);
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(refreshRequest)
                                )
                )

                // ====================================================
                // ASSERT
                // ====================================================

                // A valid refresh token should successfully
                // generate a new authentication response.
                .andExpect(status().isOk())

                // A new access token must be returned.
                .andExpect(jsonPath("$.accessToken").exists())

                // Refresh-token rotation generates a replacement
                // refresh token.
                .andExpect(jsonPath("$.refreshToken").exists())

                // Our API uses the Bearer authentication scheme.
                .andExpect(jsonPath("$.tokenType").value("Bearer"))

                // The client needs to know the access-token lifetime.
                .andExpect(jsonPath("$.accessTokenExpiresIn").exists());

    }

    @Test
    void shouldRejectInvalidRefreshToken() throws Exception {
        //Arrange
        // This token was never generated by our application and
        // therefore does not have a corresponding refresh session
        // in the database.
        RefreshTokenRequest request =
                new RefreshTokenRequest();

        request.setRefreshToken("invalid-refresh-token");
        // ============================================================
        // ACT + ASSERT
        // ============================================================
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )
                // The server must reject an unknown refresh credential.
                .andExpect(status().isUnauthorized())

                // We deliberately expose the same authentication
                // failure instead of revealing whether the token
                // existed in the database.
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));


    }

    @Test
    void shouldRejectExpiredRefreshToken() throws Exception {
        // ============================================================
        // ARRANGE - Create account
        // ============================================================
        RegisterAccountRequest registerAccountRequest = new RegisterAccountRequest();
        registerAccountRequest.setName("John Doe");
        registerAccountRequest.setEmail("john@example.com");
        registerAccountRequest.setPhone("+9779812345678");
        registerAccountRequest.setPassword("StrongPassword123");
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(registerAccountRequest)
                                )
                )
                .andExpect(status().isCreated());
        // ============================================================
        // ARRANGE - Login
        // ============================================================
        // We need the real refresh token generated by the application.
        LoginRequest loginRequest =
                new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");
        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(loginRequest)
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();

        // ============================================================
        // ARRANGE - Extract refresh token
        // ============================================================
        String refreshToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();

        // ============================================================
        // ARRANGE - Expire the refresh session
        // ============================================================
        // The token is valid, but we deliberately move its server-side
        // expiration time into the past.
        //
        // This allows us to test expiration immediately instead of
        // waiting for the configured 30-day lifetime.
        String refreshTokenHash =
                refreshTokenService.hashRefreshToken(refreshToken);
        RefreshSession refreshSession =
                refreshSessionRepository
                        .findByTokenHash(refreshTokenHash)
                        .orElseThrow();
        refreshSession.setExpiresAt(
                Instant.now().minusSeconds(60)
        );
        refreshSessionRepository.saveAndFlush(refreshSession);
        // ============================================================
        // ACT + ASSERT
        // ============================================================
        RefreshTokenRequest refreshRequest =
                new RefreshTokenRequest();

        refreshRequest.setRefreshToken(refreshToken);

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(refreshRequest)
                                )
                )
                // An expired refresh credential must be rejected.
                .andExpect(status().isUnauthorized())

                // The client receives the same generic authentication
                // failure rather than details about the session state.
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));


    }

    @Test
    void shouldRejectRevokedRefreshToken() throws Exception {
        //Arrange
        RegisterAccountRequest registerAccountRequest =
                new RegisterAccountRequest();
        registerAccountRequest.setName("John Doe");
        registerAccountRequest.setEmail("john@example.com");
        registerAccountRequest.setPhone("+9779812345678");
        registerAccountRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(registerAccountRequest)
                                )
                )
                .andExpect(status().isCreated());
        // ============================================================
        // ARRANGE - Login
        // ============================================================

        LoginRequest loginRequest =
                new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(loginRequest)
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();
        // ============================================================
        // ARRANGE - Extract refresh token
        // ============================================================

        String refreshToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();
        // ============================================================
        // ARRANGE - Revoke the refresh session
        // ============================================================
        // The token itself is still within its normal lifetime.
        // We are testing revocation as a separate security rule.
        String refreshTokenHash =
                refreshTokenService.hashRefreshToken(refreshToken);

        RefreshSession refreshSession =
                refreshSessionRepository
                        .findByTokenHash(refreshTokenHash)
                        .orElseThrow();

        refreshSession.setRevokedAt(Instant.now());
        refreshSessionRepository.saveAndFlush(refreshSession);
        // ============================================================
        // ACT + ASSERT
        // ============================================================

        RefreshTokenRequest refreshRequest =
                new RefreshTokenRequest();

        refreshRequest.setRefreshToken(refreshToken);

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(refreshRequest)
                                )
                )
                // A revoked refresh token must never issue
                // new authentication credentials.
                .andExpect(status().isUnauthorized())

                // Do not reveal whether the token was revoked,
                // expired, or simply invalid.
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));


    }

    @Test
    void shouldRejectRefreshTokenAfterLogout() throws Exception {

        // ============================================================
        // ARRANGE - Create an account
        // ============================================================
        // We need a real ACTIVE account so that we can login first.

        RegisterAccountRequest registerRequest = new RegisterAccountRequest();

        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPhone("+9779812345678");
        registerRequest.setPassword("StrongPassword123");

        String registerJson =
                objectMapper.writeValueAsString(registerRequest);

        // ============================================================
        // ACT - Register the account
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerJson)
                )
                .andExpect(status().isCreated());


        // ============================================================
        // ARRANGE - Prepare login request
        // ============================================================
        // We use the same credentials that were used during registration.

        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");

        String loginJson =
                objectMapper.writeValueAsString(loginRequest);


        // ============================================================
        // ACT + ASSERT - Login
        // ============================================================
        // Login returns the access token and refresh token.
        //
        // We use .andReturn() because we need to take those tokens
        // from the response and use them in the next requests.

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(loginJson)
                        )
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.accessToken").exists())
                        .andExpect(jsonPath("$.refreshToken").exists())
                        .andExpect(jsonPath("$.tokenType").value("Bearer"))
                        .andExpect(jsonPath("$.accessTokenExpiresIn").exists())
                        .andReturn();


        // ============================================================
        // EXTRACT ACCESS TOKEN
        // ============================================================
        // Login returned JSON.
        //
        // We read that JSON and extract the accessToken because
        // logout requires an authenticated user.

        String accessToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("accessToken")
                        .asText();


        // ============================================================
        // EXTRACT REFRESH TOKEN
        // ============================================================
        // We extract the refreshToken because logout needs to know
        // which refresh session should be revoked.
        //
        // We will also reuse this SAME token later to prove that
        // logout actually revoked it.

        String refreshToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();


        // ============================================================
        // ARRANGE - Prepare logout request
        // ============================================================
        // Logout requires the refresh token in the request body.

        RefreshTokenRequest logoutRequest =
                new RefreshTokenRequest();

        logoutRequest.setRefreshToken(refreshToken);

        String logoutJson =
                objectMapper.writeValueAsString(logoutRequest);


        // ============================================================
        // ACT + ASSERT - Logout
        // ============================================================
        // The access token proves WHO is logging out.
        //
        // The refresh token tells the server WHICH refresh session
        // should be revoked.

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                                .content(logoutJson)
                )
                .andExpect(status().isNoContent());


        // ============================================================
        // ARRANGE - Prepare refresh request using OLD token
        // ============================================================
        // IMPORTANT:
        // We intentionally use the SAME refresh token that was used
        // before logout.
        //
        // If logout worked correctly, this token is now revoked.

        RefreshTokenRequest refreshRequest =
                new RefreshTokenRequest();

        refreshRequest.setRefreshToken(refreshToken);

        String refreshJson =
                objectMapper.writeValueAsString(refreshRequest);


        // ============================================================
        // ACT + ASSERT - Try to refresh after logout
        // ============================================================
        // The refresh token was revoked during logout.
        //
        // Therefore the server must reject it with 401 Unauthorized.

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(refreshJson)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectLogoutWhenRefreshTokenBelongsToDifferentAccount() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        // ------------------------------------------------------------
        // Create Account A
        // ------------------------------------------------------------
        // Account A will own the refresh token that we will
        // try to use later in the test.
        RegisterAccountRequest accountARequest = new RegisterAccountRequest();

        accountARequest.setName("John Doe");
        accountARequest.setEmail("john@example.com");
        accountARequest.setPhone("+9779812345678");
        accountARequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(accountARequest)
                                )
                )
                .andExpect(status().isCreated());


        // ------------------------------------------------------------
        // Create Account B
        // ------------------------------------------------------------
        // Account B will be the authenticated account.
        // Later, B will try to use A's refresh token.
        RegisterAccountRequest accountBRequest = new RegisterAccountRequest();

        accountBRequest.setName("Jane Doe");
        accountBRequest.setEmail("jane@example.com");
        accountBRequest.setPhone("+9779812345679");
        accountBRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(accountBRequest)
                                )
                )
                .andExpect(status().isCreated());


        // ============================================================
        // LOGIN AS ACCOUNT A
        // ============================================================

        // Login as Account A.
        // This creates Account A's refresh session in the database.
        LoginRequest accountALogin = new LoginRequest();

        accountALogin.setEmail("john@example.com");
        accountALogin.setPassword("StrongPassword123");

        MvcResult accountALoginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        accountALogin
                                                )
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // Extract Account A's refresh token from the login response.
        //
        // We need this token because it belongs to Account A.
        String accountARefreshToken =
                objectMapper
                        .readTree(
                                accountALoginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();


        // ============================================================
        // LOGIN AS ACCOUNT B
        // ============================================================

        // Login as Account B.
        // We need B's access token because the logout endpoint
        // uses the authenticated access token to identify the account.
        LoginRequest accountBLogin = new LoginRequest();

        accountBLogin.setEmail("jane@example.com");
        accountBLogin.setPassword("StrongPassword123");

        MvcResult accountBLoginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        accountBLogin
                                                )
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // Extract Account B's access token.
        //
        // This token tells the server:
        // "The currently authenticated account is Account B."
        String accountBAccessToken =
                objectMapper
                        .readTree(
                                accountBLoginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("accessToken")
                        .asText();


        // ============================================================
        // ACT
        // ============================================================

        // Create the request object used by the logout endpoint.
        RefreshTokenRequest refreshTokenRequest =
                new RefreshTokenRequest();

        // Put Account A's refresh token into the request body.
        //
        // Remember:
        //
        // Access Token  -> Account B
        // Refresh Token -> Account A
        //
        // This is intentionally an invalid combination.
        refreshTokenRequest.setRefreshToken(accountARefreshToken);


        // Send the logout request.
        mockMvc.perform(
                        post("/api/v1/auth/logout")

                                // Authenticate the request as Account B.
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accountBAccessToken
                                )

                                .contentType(MediaType.APPLICATION_JSON)

                                // Send Account A's refresh token.
                                .content(
                                        objectMapper.writeValueAsString(
                                                refreshTokenRequest
                                        )
                                )
                )

                // Account B does not own Account A's refresh session.
                // Therefore the server must reject the request.
                .andExpect(status().isUnauthorized())

                // The application returns the generic authentication
                // error instead of revealing ownership information.
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));


        // ============================================================
        // DATABASE VERIFICATION
        // ============================================================

        // Convert Account A's raw refresh token into the SHA-256 hash
        // that is stored in the database.
        String accountARefreshTokenHash =
                refreshTokenService.hashRefreshToken(
                        accountARefreshToken
                );


        // Find Account A's refresh session using its hashed token.
        RefreshSession accountARefreshSession =
                refreshSessionRepository
                        .findByTokenHash(accountARefreshTokenHash)
                        .orElseThrow();


        // The failed logout attempt must NOT revoke Account A's session.
        //
        // revokedAt == null means the refresh session is still active.
        assertNull(accountARefreshSession.getRevokedAt());
    }

    @Test
    void shouldRejectLogoutWithInvalidRefreshToken() throws Exception {
        //Arrange
        // Create a real account.
        // We need an account so that we can obtain a valid access token.
        RegisterAccountRequest registerRequest = new RegisterAccountRequest();

        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPhone("+9779812345678");
        registerRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(registerRequest)
                                )
                )
                .andExpect(status().isCreated());

        // ============================================================
        // LOGIN
        // ============================================================
        // Login so that we receive a valid access token.
        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(loginRequest)
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();
// Extract the valid access token from the login response.
        //
        // We need this because the logout endpoint requires
        // an authenticated request.
        String accessToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("accessToken")
                        .asText();

        // ============================================================
        // CREATE INVALID REFRESH TOKEN
        // ============================================================

        // This token was NOT generated by our RefreshTokenService
        // and therefore does not have a corresponding refresh session
        // in the database.
        String invalidRefreshToken =
                "this-is-not-a-valid-refresh-token";


        // Create the logout request body.
        RefreshTokenRequest refreshTokenRequest =
                new RefreshTokenRequest();

        // Put the fake refresh token into the request.
        refreshTokenRequest.setRefreshToken(invalidRefreshToken);
        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/auth/logout")

                                // The access token is valid.
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )

                                .contentType(MediaType.APPLICATION_JSON)

                                // But the refresh token is invalid.
                                .content(
                                        objectMapper.writeValueAsString(
                                                refreshTokenRequest
                                        )
                                )
                )
                // The refresh token cannot be found in the database,
                // so logout must be rejected.
                .andExpect(status().isUnauthorized())

                // Return our generic authentication error.
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));

    }

    @Test
    void shouldRejectReuseOfOldRefreshTokenAfterRotation() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        // We need a real account because a refresh token is created
        // only after a successful login.
        RegisterAccountRequest registerRequest = new RegisterAccountRequest();

        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPhone("+9779812345678");
        registerRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(registerRequest)
                                )
                )
                .andExpect(status().isCreated());


        // ============================================================
        // ARRANGE — LOGIN
        // ============================================================

        // Login creates the first refresh token.
        // We will call this token R1.
        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(loginRequest)
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // Extract R1 from the login response.
        //
        // The server returned JSON such as:
        //
        // {
        //     "accessToken": "...",
        //     "refreshToken": "R1",
        //     ...
        // }
        //
        // We need R1 because we are going to use it
        // in the refresh request.
        String oldRefreshToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();


        // ============================================================
        // ARRANGE — BUILD REFRESH REQUEST
        // ============================================================

        // Our RefreshTokenRequest uses a no-argument constructor
        // and a setter, so we create the DTO this way.
        RefreshTokenRequest firstRefreshRequest =
                new RefreshTokenRequest();

        // Put R1 inside the request body.
        firstRefreshRequest.setRefreshToken(oldRefreshToken);


        // ============================================================
        // ACT — REFRESH USING R1
        // ============================================================

        // R1 is currently valid, so the first refresh must succeed.
        //
        // The server will rotate:
        //
        // R1 -> invalid
        // R2 -> valid
        MvcResult refreshResult =
                mockMvc.perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        firstRefreshRequest
                                                )
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT R2
        // ============================================================

        // The successful refresh returns a NEW refresh token.
        // This proves that rotation occurred.
        String newRefreshToken =
                objectMapper
                        .readTree(
                                refreshResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();


        // R1 and R2 must be different credentials.
        assertNotEquals(oldRefreshToken, newRefreshToken);


        // ============================================================
        // ACT — TRY TO REUSE R1
        // ============================================================

        // We deliberately create another request containing
        // the OLD refresh token R1.
        //
        // R1 has already been rotated away, so this request
        // represents an attempt to reuse an old credential.
        RefreshTokenRequest reusedRefreshRequest =
                new RefreshTokenRequest();

        reusedRefreshRequest.setRefreshToken(oldRefreshToken);


        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                reusedRefreshRequest
                                        )
                                )
                )

                // R1 is no longer valid after rotation.
                .andExpect(status().isUnauthorized())

                // The API returns our generic authentication failure.
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRejectRefreshWhenAccountIsSuspended() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        // We need a real ACTIVE account because only an ACTIVE
        // account is allowed to log in and receive a refresh token.
        RegisterAccountRequest registerRequest = new RegisterAccountRequest();

        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPhone("+9779812345678");
        registerRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(registerRequest)
                                )
                )
                .andExpect(status().isCreated());


        // ============================================================
        // ARRANGE — LOGIN
        // ============================================================

        // The account is currently ACTIVE, so login should succeed.
        //
        // The purpose of this login is to obtain a valid refresh token
        // BEFORE we suspend the account.
        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(loginRequest)
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // Extract the refresh token created while the account
        // was still ACTIVE.
        String refreshToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();


        // ============================================================
        // ARRANGE — FIND ACCOUNT
        // ============================================================

        // Find the account we just created.
        //
        // We need the actual database entity because we are going
        // to change its lifecycle state from ACTIVE to SUSPENDED.
        IdentityAccount account =
                identityAccountRepository
                        .findByEmail("john@example.com")
                        .orElseThrow();


        // ============================================================
        // ARRANGE — SUSPEND ACCOUNT
        // ============================================================

        // Change the account's current lifecycle state.
        //
        // Notice what we are NOT doing:
        //
        // We are NOT deleting the refresh token.
        // We are NOT revoking the refresh session.
        //
        // The refresh token itself is still valid.
        //
        // We are testing whether the refresh operation correctly
        // checks the CURRENT account status.
        account.setStatus(AccountStatus.SUSPENDED);

        identityAccountRepository.save(account);


        // ============================================================
        // ACT — TRY TO REFRESH
        // ============================================================

        // Create the refresh request using the token that was
        // legitimately issued before the account was suspended.
        RefreshTokenRequest refreshRequest =
                new RefreshTokenRequest();

        refreshRequest.setRefreshToken(refreshToken);


        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                refreshRequest
                                        )
                                )
                )

                // The refresh token itself exists and has not expired.
                // However, the account is now SUSPENDED.
                //
                // Therefore the server must reject the refresh request.
                .andExpect(status().isUnauthorized())

                // Do not reveal account-state details to the client.
                // Return the same generic authentication failure.
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRejectRefreshWhenAccountIsDeactivated() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        // Create an ACTIVE account first.
        // An account must be ACTIVE before it can successfully log in.
        RegisterAccountRequest registerRequest = new RegisterAccountRequest();

        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPhone("+9779812345678");
        registerRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(registerRequest)
                                )
                )
                .andExpect(status().isCreated());


        // ============================================================
        // ARRANGE — LOGIN
        // ============================================================

        // Login while the account is still ACTIVE.
        //
        // This gives us a legitimate refresh token.
        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(loginRequest)
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // Extract the refresh token issued during the successful login.
        String refreshToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();


        // ============================================================
        // ARRANGE — FIND ACCOUNT
        // ============================================================

        // Retrieve the actual persisted account.
        //
        // We need to change its current lifecycle state.
        IdentityAccount account =
                identityAccountRepository
                        .findByEmail("john@example.com")
                        .orElseThrow();


        // ============================================================
        // ARRANGE — DEACTIVATE ACCOUNT
        // ============================================================

        // The refresh token was issued when the account was ACTIVE.
        //
        // We now change the account to DEACTIVATED without deleting
        // the refresh session.
        account.setStatus(AccountStatus.DEACTIVATED);

        identityAccountRepository.save(account);


        // ============================================================
        // ACT — TRY TO REFRESH
        // ============================================================

        // Send the previously valid refresh token.
        RefreshTokenRequest refreshRequest =
                new RefreshTokenRequest();

        refreshRequest.setRefreshToken(refreshToken);


        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                refreshRequest
                                        )
                                )
                )

                // The refresh token exists, but its owning account
                // is no longer ACTIVE.
                //
                // Therefore the server must reject the request.
                .andExpect(status().isUnauthorized())

                // Return the same generic authentication error.
                // We do not reveal that the account was deactivated.
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRejectTamperedRefreshToken() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        // We need a real account because a valid refresh token
        // can only be obtained through a successful login.
        RegisterAccountRequest registerRequest = new RegisterAccountRequest();

        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPhone("+9779812345678");
        registerRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(registerRequest)
                                )
                )
                .andExpect(status().isCreated());


        // ============================================================
        // ARRANGE — LOGIN
        // ============================================================

        // Login using the correct credentials so the application
        // creates a legitimate refresh token.
        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(loginRequest)
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT VALID REFRESH TOKEN
        // ============================================================

        // Read the refresh token returned by the login endpoint.
        //
        // At this point the token is completely legitimate.
        String refreshToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();


        // ============================================================
        // ARRANGE — TAMPER WITH THE TOKEN
        // ============================================================

        // We simulate an attacker modifying the token.
        //
        // We change only the final character.
        //
        // This is important because everything else remains the same.
        // The attacker is trying to turn a valid credential into
        // a different credential.
        char lastCharacter =
                refreshToken.charAt(refreshToken.length() - 1);

        char replacement =
                lastCharacter == 'A' ? 'B' : 'A';

        String tamperedRefreshToken =
                refreshToken.substring(0, refreshToken.length() - 1)
                        + replacement;


        // ============================================================
        // ACT — TRY TO REFRESH USING TAMPERED TOKEN
        // ============================================================

        // Send the modified token to the refresh endpoint.
        RefreshTokenRequest refreshRequest =
                new RefreshTokenRequest();

        refreshRequest.setRefreshToken(tamperedRefreshToken);


        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                refreshRequest
                                        )
                                )
                )

                // The modified token does not match the hash stored
                // for the legitimate refresh session.
                //
                // Therefore the server must reject it.
                .andExpect(status().isUnauthorized())

                // We deliberately return a generic authentication
                // failure rather than revealing exactly why the
                // token was rejected.
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRejectRefreshWhenRefreshTokenIsMissing() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        // We deliberately create a request without a refresh token.
        //
        // The refresh endpoint requires a refresh token to identify
        // the refresh session that should be used.
        RefreshTokenRequest request =
                new RefreshTokenRequest();

        // We intentionally do NOT set the refresh token.


        // ============================================================
        // ACT + ASSERT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )

                // No refresh token means the client has not supplied
                // the credential required to perform the operation.
                .andExpect(status().isBadRequest())

                // This should be handled by our request validation
                // rather than reaching the refresh-token service.
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_ERROR"))

                // Verify that the response identifies the missing
                // refreshToken field.
                .andExpect(jsonPath("$.message")
                        .exists())

                // Verify the standard API error metadata.
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/auth/refresh"));
    }


    @Test
    void shouldRejectLogoutWithoutAccessToken() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        // Logout requires a RefreshTokenRequest.
        //
        // We provide a value so that request validation does not
        // interfere with the security behavior we are testing.
        RefreshTokenRequest request =
                new RefreshTokenRequest();

        request.setRefreshToken("some-refresh-token");


        // ============================================================
        // ACT + ASSERT
        // ============================================================

        // We deliberately DO NOT provide an Authorization header.
        //
        // Therefore Spring Security cannot authenticate the request.
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(request)
                                )
                )

                // Logout is a protected operation.
                //
                // Without an authenticated access token,
                // the request must be rejected.
                // Our SecurityConfig says that every request other
                // than the explicitly permitted endpoints requires
                // authentication.
                //
                // Because there is no authenticated user, Spring
                // Security rejects the request.
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectLogoutWhenAccessTokenIsTampered() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        // We need a real account because we need the application
        // to generate a genuine, correctly signed access token.
        RegisterAccountRequest registerRequest =
                new RegisterAccountRequest();

        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPhone("+9779812345678");
        registerRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                registerRequest
                                        )
                                )
                )
                .andExpect(status().isCreated());


        // ============================================================
        // ARRANGE — LOGIN
        // ============================================================

        // Login using the account we just created.
        //
        // A successful login generates a genuine access token.
        LoginRequest loginRequest =
                new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        loginRequest
                                                )
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT ACCESS TOKEN
        // ============================================================

        // Read the genuine access token from the login response.
        //
        // We need the real token before we can deliberately modify it.
        String accessToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("accessToken")
                        .asText();


        // ============================================================
        // ARRANGE — TAMPER WITH ACCESS TOKEN
        // ============================================================

        // Get the final character of the genuine JWT.
        char lastCharacter =
                accessToken.charAt(accessToken.length() - 1);

        // Choose a different character.
        //
        // If the last character is A, replace it with B.
        // Otherwise replace it with A.
        char replacement =
                lastCharacter == 'A' ? 'B' : 'A';

        // Keep the entire token except the final character,
        // then append our replacement character.
        //
        // This creates a token that is different from the
        // genuine token issued by our authentication service.
        String tamperedAccessToken =
                accessToken.substring(0, accessToken.length() - 1)
                        + replacement;


        // ============================================================
        // ACT
        // ============================================================

        // Create the logout request using the no-argument constructor.
        //
        // RefreshTokenRequest does not have a constructor that accepts
        // a String, so we create the object first and then use the setter.
        RefreshTokenRequest logoutRequest =
                new RefreshTokenRequest();

        // We provide a refresh-token value because this test is NOT
        // testing refresh-token validation.
        //
        // The purpose of this test is to verify that the tampered
        // ACCESS TOKEN is rejected by Spring Security first.
        logoutRequest.setRefreshToken("some-refresh-token");


        // Send the tampered access token to the protected logout endpoint.
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + tamperedAccessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                logoutRequest
                                        )
                                )
                )


                // ====================================================
                // ASSERT
                // ====================================================

                // The access token was modified after it was signed.
                //
                // Therefore its JWT signature should no longer be valid.
                //
                // Spring Security must reject the request.
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRefreshWhenRefreshTokenIsTampered() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        // We need a real account because the login operation must
        // generate a real refresh token for us.
        RegisterAccountRequest registerRequest =
                new RegisterAccountRequest();

        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPhone("+9779812345678");
        registerRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                registerRequest
                                        )
                                )
                )
                .andExpect(status().isCreated());


        // ============================================================
        // ARRANGE — LOGIN
        // ============================================================

        // Login so the application generates a genuine refresh token.
        LoginRequest loginRequest =
                new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        loginRequest
                                                )
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT REAL REFRESH TOKEN
        // ============================================================

        // Read the refresh token from the login response.
        //
        // This is the genuine token stored indirectly by the system.
        // The database stores its hash, not this raw value.
        String refreshToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();


        // ============================================================
        // ARRANGE — TAMPER WITH REFRESH TOKEN
        // ============================================================

        // Get the final character of the genuine refresh token.
        char lastCharacter =
                refreshToken.charAt(refreshToken.length() - 1);

        // Choose a different character.
        //
        // We only need to change the token slightly so that it becomes
        // a completely different credential.
        char replacement =
                lastCharacter == 'A' ? 'B' : 'A';

        // Keep every character except the last one and replace
        // that final character.
        String tamperedRefreshToken =
                refreshToken.substring(0, refreshToken.length() - 1)
                        + replacement;


        // Make sure our test actually created a different token.
        assertNotEquals(
                refreshToken,
                tamperedRefreshToken
        );


        // ============================================================
        // ACT
        // ============================================================

        // Create the refresh request using the no-argument constructor.
        RefreshTokenRequest refreshRequest =
                new RefreshTokenRequest();

        // Send the tampered token instead of the genuine token.
        refreshRequest.setRefreshToken(tamperedRefreshToken);


        // Attempt to refresh using the modified token.
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                refreshRequest
                                        )
                                )
                )


                // ====================================================
                // ASSERT
                // ====================================================

                // The tampered token must be rejected.
                //
                // The modified token produces a different hash, so it
                // cannot match the refresh-token hash stored in the DB.
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRefreshWhenRefreshTokenIsMalformed() throws Exception {

        // ============================================================
        // ARRANGE
        // ============================================================

        // Create the request using the no-argument constructor.
        RefreshTokenRequest refreshRequest =
                new RefreshTokenRequest();

        // This is not a token generated by PulsePay.
        // We are testing how the refresh endpoint handles
        // a completely invalid credential.
        refreshRequest.setRefreshToken("not-a-valid-refresh-token");


        // ============================================================
        // ACT
        // ============================================================

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                refreshRequest
                                        )
                                )
                )


                // ====================================================
                // ASSERT
                // ====================================================

                // The supplied credential is invalid and therefore
                // must not result in a successful refresh.
                .andExpect(status().isUnauthorized());
    }
    @Test
    void shouldRejectRefreshWhenRefreshSessionIsExpired() throws Exception {

        // ============================================================
        // ARRANGE — CREATE ACCOUNT
        // ============================================================

        // We need a real account because a refresh session belongs
        // to an IdentityAccount.
        RegisterAccountRequest registerRequest =
                new RegisterAccountRequest();

        registerRequest.setName("John Doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPhone("+9779812345678");
        registerRequest.setPassword("StrongPassword123");

        mockMvc.perform(
                        post("/api/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                registerRequest
                                        )
                                )
                )
                .andExpect(status().isCreated());


        // ============================================================
        // ARRANGE — LOGIN
        // ============================================================

        // Login so the application creates a genuine refresh session.
        LoginRequest loginRequest =
                new LoginRequest();

        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("StrongPassword123");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        loginRequest
                                                )
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();


        // ============================================================
        // ARRANGE — EXTRACT REAL REFRESH TOKEN
        // ============================================================

        // Extract the raw refresh token returned by login.
        //
        // We need the raw token because this is the credential that
        // the client will later send to /auth/refresh.
        String refreshToken =
                objectMapper
                        .readTree(
                                loginResult
                                        .getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();


        // ============================================================
        // ARRANGE — FIND THE REFRESH SESSION
        // ============================================================

        // The database does not store the raw refresh token.
        //
        // It stores SHA-256(refreshToken), so calculate the same hash
        // in the test to locate the session created by login.
        String refreshTokenHash =
                refreshTokenService.hashRefreshToken(refreshToken);

        RefreshSession refreshSession =
                refreshSessionRepository
                        .findByTokenHash(refreshTokenHash)
                        .orElseThrow();


        // ============================================================
        // ARRANGE — EXPIRE THE SESSION
        // ============================================================

        // Move the expiration time into the past.
        //
        // This simulates what the database would look like after the
        // refresh session has naturally expired.
        refreshSession.setExpiresAt(
                Instant.now().minusSeconds(60)
        );

        // Persist the changed expiration time.
        refreshSessionRepository.save(refreshSession);


        // ============================================================
        // ACT
        // ============================================================

        // Try to use the refresh token after its session has expired.
        RefreshTokenRequest refreshRequest =
                new RefreshTokenRequest();

        refreshRequest.setRefreshToken(refreshToken);

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                refreshRequest
                                        )
                                )
                )


                // ====================================================
                // ASSERT
                // ====================================================

                // The refresh session is expired, so the application
                // must refuse to issue new authentication tokens.
                .andExpect(status().isUnauthorized());
    }



}
