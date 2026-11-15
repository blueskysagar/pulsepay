package com.pulsepay.notification_service.integration;
import com.pulsepay.notification_service.messaging.TransferCompletedListener;
import com.pulsepay.notification_service.messaging.event.TransferCompletedEvent;
import com.pulsepay.notification_service.service.NotificationProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class TransferCompletedListenerTest {
    private ObjectMapper objectMapper;
    private NotificationProcessingService notificationProcessingService;
    private TransferCompletedListener listener;
    @BeforeEach
    void setUp(){
        objectMapper = new ObjectMapper();
        notificationProcessingService =
                Mockito.mock(NotificationProcessingService.class);
        listener = new TransferCompletedListener(
                objectMapper,
                notificationProcessingService
        );
    }
    @Test
    void shouldProcessValidTransferCompletedEvent() throws Exception {

        // ARRANGE: create a valid transfer event
        UUID transferId = UUID.randomUUID();
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        TransferCompletedEvent event = new TransferCompletedEvent(
                transferId,
                sourceWalletId,
                destinationWalletId,
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("25.00"),
                "USD"
        );

        String eventId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(event);

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "pulsepay.transfer.completed",
                        0,
                        0L,
                        transferId.toString(),
                        payload
                );

        record.headers().add(
                "event_id",
                eventId.getBytes(StandardCharsets.UTF_8)
        );

        // ACT: let the listener process the record
        listener.onTransferCompleted(record);

        // ASSERT: verify the service received the correct arguments
        ArgumentCaptor<TransferCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(TransferCompletedEvent.class);

        ArgumentCaptor<String> eventIdCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(notificationProcessingService, times(1))
                .processTransferCompleted(
                        eventCaptor.capture(),
                        eventIdCaptor.capture()
                );

        TransferCompletedEvent actualEvent = eventCaptor.getValue();

        assertEquals(transferId, actualEvent.transferId());
        assertEquals(sourceWalletId, actualEvent.sourceWalletId());
        assertEquals(destinationWalletId, actualEvent.destinationWalletId());
        assertEquals(sourceAccountId, actualEvent.sourceAccountId());
        assertEquals(destinationAccountId, actualEvent.destinationAccountId());
        assertEquals(
                0,
                new BigDecimal("25.00").compareTo(actualEvent.amount())
        );
        assertEquals("USD", actualEvent.currency());
        assertEquals(eventId, eventIdCaptor.getValue());
    }
    @Test
    void shouldRejectEventWhenEventIdHeaderIsMissing() {

        // ARRANGE: create a record without the required event_id header
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "pulsepay.transfer.completed",
                        0,
                        0L,
                        "transfer-key",
                        "{}"
                );

        // ACT + ASSERT: the listener must reject the record
        assertThrows(
                IllegalStateException.class,
                () -> listener.onTransferCompleted(record)
        );

        // The invalid record must never reach the service
        verifyNoInteractions(notificationProcessingService);
    }

    @Test
    void shouldRejectMalformedJson() {

        // ARRANGE: provide malformed JSON but a valid event_id header
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(
                        "pulsepay.transfer.completed",
                        0,
                        0L,
                        "transfer-key",
                        "{invalid-json"
                );

        record.headers().add(
                "event_id",
                "event-123".getBytes(StandardCharsets.UTF_8)
        );

        // ACT + ASSERT: Jackson should reject the malformed payload
        assertThrows(
                JacksonException.class,
                () -> listener.onTransferCompleted(record)
        );

        // Deserialization failed, so the service must not be called
        verifyNoInteractions(notificationProcessingService);
    }


}
