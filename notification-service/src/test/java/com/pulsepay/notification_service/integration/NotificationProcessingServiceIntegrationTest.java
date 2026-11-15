package com.pulsepay.notification_service.integration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import com.pulsepay.notification_service.dto.response.NotificationResponse;
import com.pulsepay.notification_service.entity.Notification;
import com.pulsepay.notification_service.exception.NotificationNotFoundException;
import com.pulsepay.notification_service.messaging.event.TransferCompletedEvent;
import com.pulsepay.notification_service.repository.NotificationRepository;
import com.pulsepay.notification_service.service.NotificationProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test") //Instructs spring boot to load application specific configuration from propertie.yml

public class NotificationProcessingServiceIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");
    @Container
    static KafkaContainer kafka =
            new KafkaContainer(
                    DockerImageName.parse("apache/kafka:3.9.1")
            ).withStartupTimeout(Duration.ofMinutes(3));

    //code asks the running kakfa which port do you listen to and the code hands the port to spring boot now we can publish and send kafka message.
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.kafka.bootstrap-servers",
                kafka::getBootstrapServers
        );
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificationProcessingService notificationProcessingService;
    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();

    }

    @Test
    void shouldCreateSenderAndReceiverNotificationsForCompletedTransfer() {
        //Arrange-prepare completed transfer event.
        UUID transferId = UUID.randomUUID();
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        String eventId = UUID.randomUUID().toString();
        TransferCompletedEvent event = new TransferCompletedEvent(
                transferId,
                sourceWalletId,
                destinationWalletId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency
        );
        //Act process the event through real service
        notificationProcessingService.processTransferCompleted(
                event,
                eventId
        );
        //Assert: exactly two notifications are saved
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(2);
        //Verify the sender notification
        assertThat(notifications)
                .anySatisfy(notification -> {
                    assertThat(notification.getRecipientAccountId())
                            .isEqualTo(sourceAccountId);
                    assertThat(notification.getEventId()).isEqualTo(eventId);
                    assertThat(notification.getTransferId()).isEqualTo(transferId);
                    assertThat(notification.getNotificationType())
                            .isEqualTo("TRANSFER_SENT");
                    assertThat(notification.getMessage())
                            .isEqualTo("You sent 100.00 USD");
                });

        // Verify the receiver notification.
        assertThat(notifications)
                .anySatisfy(notification -> {
                    assertThat(notification.getRecipientAccountId())
                            .isEqualTo(destinationAccountId);
                    assertThat(notification.getEventId()).isEqualTo(eventId);
                    assertThat(notification.getTransferId()).isEqualTo(transferId);
                    assertThat(notification.getNotificationType())
                            .isEqualTo("TRANSFER_RECEIVED");
                    assertThat(notification.getMessage())
                            .isEqualTo("You received 100.00 USD");
                });


    }

    @Test
    void shouldNotCreateDuplicateNotificationsWhenEventIsProcessingTwice() {
        // ARRANGE: both deliveries use the same event ID.
        UUID transferId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        TransferCompletedEvent event = new TransferCompletedEvent(
                transferId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("25.00"),
                "USD"
        );
        // ACT: simulate redelivery of the same event.
        notificationProcessingService.processTransferCompleted(event, eventId);
        notificationProcessingService.processTransferCompleted(event, eventId);

        // ASSERT: the database unique constraint prevents duplicate rows.
        List<Notification> notifications = notificationRepository.findAll();

        assertThat(notifications).hasSize(2);
        assertThat(notifications)
                .allSatisfy(notification ->
                        assertThat(notification.getEventId()).isEqualTo(eventId)
                );

    }

    @Test
    void shouldReturnOnlyTheAccountNotificationsUsingPagination() {
        // ARRANGE: create two transfers involving account A.
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        UUID accountC = UUID.randomUUID();

        UUID firstTransferId = UUID.randomUUID();

        TransferCompletedEvent firstEvent = new TransferCompletedEvent(
                firstTransferId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                accountA,
                accountB,
                new BigDecimal("10.00"),
                "USD"
        );

        UUID secondTransferId = UUID.randomUUID();

        TransferCompletedEvent secondEvent = new TransferCompletedEvent(
                secondTransferId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                accountC,
                accountA,
                new BigDecimal("20.00"),
                "USD"
        );

        notificationProcessingService.processTransferCompleted(
                firstEvent,
                UUID.randomUUID().toString()
        );

        notificationProcessingService.processTransferCompleted(
                secondEvent,
                UUID.randomUUID().toString()
        );
        // ACT: request page zero, with one notification per page.
        Page<NotificationResponse> page =
                notificationProcessingService.getMyNotifications(
                        accountA,
                        PageRequest.of(0, 1)
                );

        // ASSERT: account A owns two notifications in total.
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);

        // The item must belong to one of account A's two transfers.
        assertThat(page.getContent().getFirst().transferId())
                .isIn(firstTransferId, secondTransferId);
    }

    @Test
    void shouldMarkNotificationAsReadAndPreserveFirstReadTimestamp() {

        // ARRANGE: create a notification for account A.
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        TransferCompletedEvent event = new TransferCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                accountA,
                accountB,
                new BigDecimal("15.00"),
                "USD"
        );

        notificationProcessingService.processTransferCompleted(
                event,
                UUID.randomUUID().toString()
        );

        Notification notification = notificationRepository
                .findAllByRecipientAccountIdOrderByCreatedAtDesc(
                        accountA,
                        PageRequest.of(0, 10)
                )
                .getContent()
                .getFirst();

        assertThat(notification.getReadAt()).isNull();

        // ACT: mark it read twice.
        notificationProcessingService.markAsRead(accountA, notification.getId());

        Instant firstReadTimestamp = notificationRepository
                .findByIdAndRecipientAccountId(notification.getId(), accountA)
                .orElseThrow()
                .getReadAt();

        notificationProcessingService.markAsRead(accountA, notification.getId());

        Instant secondReadTimestamp = notificationRepository
                .findByIdAndRecipientAccountId(notification.getId(), accountA)
                .orElseThrow()
                .getReadAt();

        // ASSERT: the first read timestamp is preserved.
        assertThat(firstReadTimestamp).isNotNull();
        assertThat(secondReadTimestamp).isEqualTo(firstReadTimestamp);
    }

    @Test
    void shouldNotAllowAnotherAccountToMarkNotificationAsRead() {

        // ARRANGE: create a notification owned by account A.
        UUID ownerAccountId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();

        TransferCompletedEvent event = new TransferCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ownerAccountId,
                otherAccountId,
                new BigDecimal("30.00"),
                "USD"
        );

        notificationProcessingService.processTransferCompleted(
                event,
                UUID.randomUUID().toString()
        );

        Notification ownedNotification = notificationRepository
                .findAllByRecipientAccountIdOrderByCreatedAtDesc(
                        ownerAccountId,
                        PageRequest.of(0, 10)
                )
                .getContent()
                .getFirst();

        // ACT + ASSERT: the other account cannot mark it as read.
        assertThatThrownBy(() ->
                notificationProcessingService.markAsRead(
                        otherAccountId,
                        ownedNotification.getId()
                )
        ).isInstanceOf(NotificationNotFoundException.class);

        // Confirm the rejected operation did not change the owner's record.
        Notification unchangedNotification = notificationRepository
                .findByIdAndRecipientAccountId(
                        ownedNotification.getId(),
                        ownerAccountId
                )
                .orElseThrow();

        assertThat(unchangedNotification.getReadAt()).isNull();
    }

    @Test
    void shouldThrowNotFoundWhenNotificationDoesNotExistForAccount() {

        // ARRANGE: use an ID that does not exist.
        UUID accountId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        // ACT + ASSERT: expect the domain-level not-found exception.
        assertThatThrownBy(() ->
                notificationProcessingService.markAsRead(
                        accountId,
                        notificationId
                )
        ).isInstanceOf(NotificationNotFoundException.class);
    }


    @Test
    void shouldReturnNotificationsForAuthenticatedAccount() throws Exception {

        // ARRANGE: create notifications for two different accounts.
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        TransferCompletedEvent event = new TransferCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                accountA,
                accountB,
                new BigDecimal("25.00"),
                "USD"
        );

        notificationProcessingService.processTransferCompleted(
                event,
                UUID.randomUUID().toString()
        );

        var authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        accountA.toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        // ACT + ASSERT: authenticate as account A and request notifications.
        mockMvc.perform(
                        get("/api/v1/notifications")
                                .with(authentication(authenticationToken))
                                .param("page", "0")
                                .param("size", "20")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldMarkOwnedNotificationAsRead() throws Exception {

        // ARRANGE: create a notification owned by account A.
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        TransferCompletedEvent event = new TransferCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                accountA,
                accountB,
                new BigDecimal("25.00"),
                "USD"
        );

        notificationProcessingService.processTransferCompleted(
                event,
                UUID.randomUUID().toString()
        );

        Notification notification = notificationRepository
                .findAllByRecipientAccountIdOrderByCreatedAtDesc(
                        accountA,
                        PageRequest.of(0, 10)
                )
                .getContent()
                .getFirst();

        var authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        accountA.toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        // ACT + ASSERT: mark the owner's notification as read.
        mockMvc.perform(

                        patch(
                                "/api/v1/notifications/{notificationId}/read",
                                notification.getId()
                        ).with(authentication(authenticationToken))
                                .with(csrf())
                )
                .andExpect(status().isNoContent());

        // Verify the database state, not just the HTTP response.
        Notification updatedNotification = notificationRepository
                .findByIdAndRecipientAccountId(
                        notification.getId(),
                        accountA
                )
                .orElseThrow();

        assertThat(updatedNotification.getReadAt()).isNotNull();
    }

    @Test
    void shouldPreventCrossAccountAccess() throws Exception {

        // ARRANGE: account A owns the notification.
        UUID ownerAccountId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();

        TransferCompletedEvent event = new TransferCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ownerAccountId,
                otherAccountId,
                new BigDecimal("30.00"),
                "USD"
        );

        notificationProcessingService.processTransferCompleted(
                event,
                UUID.randomUUID().toString()
        );

        Notification notification = notificationRepository
                .findAllByRecipientAccountIdOrderByCreatedAtDesc(
                        ownerAccountId,
                        PageRequest.of(0, 10)
                )
                .getContent()
                .getFirst();

        var authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        otherAccountId.toString(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );

        // ACT + ASSERT: account B attempts to mark account A's notification.
        mockMvc.perform(
                        patch(
                                "/api/v1/notifications/{notificationId}/read",
                                notification.getId()
                        )
                                .with(authentication(authenticationToken))
                                .with(csrf())
                ) // <--- ADD THIS CLOSING PARENTHESIS HERE
                .andExpect(status().isNotFound());

        // Verify the owner's notification was not modified.
        Notification unchangedNotification = notificationRepository
                .findByIdAndRecipientAccountId(
                        notification.getId(),
                        ownerAccountId
                )
                .orElseThrow();

        assertThat(unchangedNotification.getReadAt()).isNull();
    }

}
