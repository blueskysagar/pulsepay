package com.pulsepay.wallet_service.integration;

import com.pulsepay.wallet_service.dto.event.TransferCompletedEvent;
import com.pulsepay.wallet_service.entity.TransferCompletionRecord;
import com.pulsepay.wallet_service.messaging.consumer.ProcessedEvent;
import com.pulsepay.wallet_service.messaging.consumer.ProcessedEventService;
import com.pulsepay.wallet_service.messaging.consumer.TransferCompletedEventConsumer;
import com.pulsepay.wallet_service.repository.ProcessedEventRepository;
import com.pulsepay.wallet_service.repository.TransferCompletionRecordRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.Uuid;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@SpringBootTest //Tell Spring boot to start their application context for this test
@Testcontainers//Tells junit that this test uses the container just manage their lifecycle
@ActiveProfiles("test") //Tells spring boot to use application.yml or properties application instead of normal app settings so test runs in fake environment and the data wont be corrupted or modified
public class ProcessedEventServiceIntegrationTest {
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
    @Autowired
    private ProcessedEventService processedEventService;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private TransferCompletionRecordRepository transferCompletionRecordRepository;
    @Autowired
    private JsonMapper objectMapper;
    @Autowired
    private TransferCompletedEventConsumer transferCompletedEventConsumer;
    @BeforeEach
    void setUp(){
        transferCompletionRecordRepository.deleteAll();
        processedEventRepository.deleteAll();
    }
    @Test
    void shouldProcessTransferCompletedEventOnlyOnce(){
        // ============================================================
        // ARRANGE — CREATE EVENT ID
        // ============================================================
        UUID eventId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();
        //We are creating fake event for our test
        TransferCompletedEvent event = new TransferCompletedEvent(
                transferId,
                sourceWalletId,
                destinationWalletId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                "USD"

        );
        //Act
        //Now we actually call our service
        boolean result = processedEventService.process(eventId,
                "TRANSFER_COMPLETED",
                event);
        //Assert(first event must be processed)
        assertThat(result)
                .isTrue();
        // ============================================================
        // ASSERT — ONE PROCESSED EVENT MUST EXIST
        // ============================================================
        assertThat(
                processedEventRepository.findAll()
        ).hasSize(1);
        // ============================================================
        // ASSERT — ONE COMPLETION RECORD MUST EXIST
        // ============================================================
        assertThat(
                transferCompletionRecordRepository.findAll()
        ).hasSize(1);
        // ============================================================
        // ACT — PROCESS THE SAME EVENT AGAIN
        // ============================================================

        /*
         * We are simulating Kafka delivering the same event again.
         *
         * Same eventId.
         * Same event.
         *
         * This is the duplicate scenario we want to protect against.
         */
        boolean duplicateResult = processedEventService.process(
                eventId,
                "TRANSFER_COMPLETED",
                event
        );
        // ============================================================
        // ASSERT — DUPLICATE EVENT MUST BE IGNORED
        // ============================================================

        /*
         * The event was already processed.
         *
         * Therefore tryClaim() should return 0
         * and the service should return false.
         */
        assertThat(duplicateResult).isFalse();
        // ============================================================
        // ASSERT — DATABASE MUST STILL CONTAIN ONE EVENT
        // ============================================================

        /*
         * The duplicate must NOT create another
         * processed_events row.
         */
        assertThat(
                processedEventRepository.findAll()
        ).hasSize(1);
        // ============================================================
        // ASSERT — DATABASE MUST STILL CONTAIN ONE RECORD
        // ============================================================

        /*
         * Most importantly, the business side effect
         * must happen only once.
         */

        assertThat(
                transferCompletionRecordRepository.findAll()
        ).hasSize(1);


    }
    @Test
    void shouldRollbackProcessedEventWhenTransferCompletionRecordFails(){
        //Arrange -Create event id
        /*
         * This event ID will be used by both the existing
         * completion record and the new event we are processing.
         */
        UUID eventId = UUID.randomUUID();
        UUID transferId =
                UUID.randomUUID();

        UUID sourceWalletId =
                UUID.randomUUID();

        UUID destinationWalletId =
                UUID.randomUUID();

        TransferCompletedEvent event =
                new TransferCompletedEvent(
                        transferId,
                        sourceWalletId,
                        destinationWalletId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("100.00"),
                        "USD"
                );
        // ============================================================
        // ARRANGE — CREATE EXISTING COMPLETION RECORD
        // ============================================================

        /*
         * The event ID is already present in the
         * transfer_completion_records table.
         *
         * Therefore, when the service tries to create another
         * completion record with the same event ID, PostgreSQL
         * will reject it because event_id is UNIQUE.
         */
        TransferCompletionRecord existingRecord =
                new TransferCompletionRecord(
                        transferId,
                        eventId,
                        new BigDecimal("100.00"),
                        "USD",
                        Instant.now()
                );

        transferCompletionRecordRepository.saveAndFlush(
                existingRecord
        );
        // ============================================================
        // ACT — PROCESS EVENT
        // ============================================================

        /*
         * The service should:
         *
         * 1. Successfully insert the event into processed_events.
         * 2. Fail while inserting the completion record.
         * 3. Roll back the entire transaction.
         */
        assertThatThrownBy(() -> processedEventService.process(
                eventId,
                "TRANSFER_COMPLETED",
                event
        )).isInstanceOf(Exception.class);
        // ============================================================
        // ASSERT — PROCESSED EVENT WAS ROLLED BACK
        // ============================================================

        /*
         * Even though tryClaim() initially inserted the event,
         * that insert belonged to the same transaction.
         *
         * Because the completion record failed, the transaction
         * was rolled back.
         *
         * Therefore, processed_events must contain nothing.
         */
        List<ProcessedEvent> processedEvents = processedEventRepository.findAll();
        assertThat(processedEvents).isEmpty();
        // ============================================================
        // ASSERT — EXISTING COMPLETION RECORD STILL EXISTS
        // ============================================================

        /*
         * The original completion record was created before
         * the transaction we are testing.
         *
         * Therefore, it must still exist.
         */
        List<TransferCompletionRecord> completionRecords = transferCompletionRecordRepository.findAll();
        assertThat(completionRecords).hasSize(1);
    }
    @Test
    void shouldRejectEventWhenEventIdHeaderIsMissing(){
        // ============================================================
        // ARRANGE — CREATE TRANSFER EVENT
        // ============================================================
        UUID transferId = UUID.randomUUID();
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();
        TransferCompletedEvent event =
                new TransferCompletedEvent(
                        transferId,
                        sourceWalletId,
                        destinationWalletId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("100.00"),
                        "USD"
                );
        // ============================================================
        // ARRANGE — CREATE KAFKA RECORD WITHOUT EVENT ID
        // ============================================================

        /*
         * We deliberately do NOT add the "event-id" header.
         *
         * This simulates a malformed Kafka message.
         */
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "pulsepay.transfer.completed",
                        0,
                        0L,
                        transferId.toString(),
                        objectMapper.writeValueAsString(event)
                );

        // ============================================================
        // ACT + ASSERT — CONSUMER SHOULD REJECT EVENT
        // ============================================================

        assertThatThrownBy(
                () -> transferCompletedEventConsumer.consume(record)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Missing event-id Kafka header");

        // ============================================================
        // ASSERT — NOTHING WAS PROCESSED
        // ============================================================

        /*
         * Because the event did not contain an event ID,
         * the consumer must reject it before processing.
         */
        assertThat(
                processedEventRepository.findAll()
        ).isEmpty();

        assertThat(
                transferCompletionRecordRepository.findAll()
        ).isEmpty();

    }
    @Test
    void shouldRejectEventWhenEventIdHeaderIsInvalid() {

        // ============================================================
        // ARRANGE — CREATE TRANSFER EVENT
        // ============================================================

        UUID transferId =
                UUID.randomUUID();

        UUID sourceWalletId =
                UUID.randomUUID();

        UUID destinationWalletId =
                UUID.randomUUID();

        TransferCompletedEvent event =
                new TransferCompletedEvent(
                        transferId,
                        sourceWalletId,
                        destinationWalletId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new BigDecimal("100.00"),
                        "USD"
                );

        // ============================================================
        // ARRANGE — CREATE KAFKA RECORD WITH INVALID EVENT ID
        // ============================================================

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "pulsepay.transfer.completed",
                        0,
                        0L,
                        transferId.toString(),
                        objectMapper.writeValueAsString(event)
                );

        /*
         * The header exists, but "not-a-uuid" is not a valid UUID.
         */
        record.headers().add(
                "event-id",
                "not-a-uuid".getBytes(StandardCharsets.UTF_8)
        );

        // ============================================================
        // ACT + ASSERT — CONSUMER SHOULD REJECT EVENT
        // ============================================================

        assertThatThrownBy(
                () -> transferCompletedEventConsumer.consume(record)
        )
                .isInstanceOf(IllegalArgumentException.class);

        // ============================================================
        // ASSERT — NOTHING WAS PROCESSED
        // ============================================================

        /*
         * The invalid event ID means the consumer cannot
         * safely identify the event.
         *
         * Therefore, nothing should be stored.
         */
        assertThat(
                processedEventRepository.findAll()
        ).isEmpty();

        assertThat(
                transferCompletionRecordRepository.findAll()
        ).isEmpty();
    }
    @Test
    void shouldCreateTransferCompletionRecordForValidEvent() {

        // ============================================================
        // ARRANGE — CREATE EVENT ID
        // ============================================================

        UUID eventId =
                UUID.randomUUID();

        // ============================================================
        // ARRANGE — CREATE TRANSFER DATA
        // ============================================================

        UUID transferId =
                UUID.randomUUID();

        UUID sourceWalletId =
                UUID.randomUUID();

        UUID destinationWalletId =
                UUID.randomUUID();

        BigDecimal amount =
                new BigDecimal("250.00");

        String currency =
                "USD";

        // ============================================================
        // ARRANGE — CREATE TRANSFER EVENT
        // ============================================================

        TransferCompletedEvent event =
                new TransferCompletedEvent(
                        transferId,
                        sourceWalletId,
                        destinationWalletId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        amount,
                        currency
                );

        // ============================================================
        // ARRANGE — CREATE KAFKA RECORD
        // ============================================================

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "pulsepay.transfer.completed",
                        0,
                        0L,
                        transferId.toString(),
                        objectMapper.writeValueAsString(event)
                );

        // ============================================================
        // ARRANGE — ADD EVENT ID HEADER
        // ============================================================

        record.headers().add(
                "event-id",
                eventId.toString().getBytes(StandardCharsets.UTF_8)
        );

        // ============================================================
        // ACT — CONSUME EVENT
        // ============================================================

        transferCompletedEventConsumer.consume(record);

        // ============================================================
        // ASSERT — EVENT WAS CLAIMED
        // ============================================================

        List<ProcessedEvent> processedEvents =
                processedEventRepository.findAll();

        assertThat(processedEvents)
                .hasSize(1);

        assertThat(processedEvents.get(0).getEventId())
                .isEqualTo(eventId);

        assertThat(processedEvents.get(0).getEventType())
                .isEqualTo("TRANSFER_COMPLETED");

        // ============================================================
        // ASSERT — COMPLETION RECORD WAS CREATED
        // ============================================================

        List<TransferCompletionRecord> completionRecords =
                transferCompletionRecordRepository.findAll();

        assertThat(completionRecords)
                .hasSize(1);

        TransferCompletionRecord completionRecord =
                completionRecords.get(0);

        assertThat(completionRecord.getTransferId())
                .isEqualTo(transferId);

        assertThat(completionRecord.getEventId())
                .isEqualTo(eventId);

        assertThat(completionRecord.getAmount())
                .isEqualByComparingTo(amount);

        assertThat(completionRecord.getCurrency())
                .isEqualTo(currency);
    }






}
