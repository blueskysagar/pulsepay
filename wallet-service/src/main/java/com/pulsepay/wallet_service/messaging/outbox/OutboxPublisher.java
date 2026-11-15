package com.pulsepay.wallet_service.messaging.outbox;

import com.pulsepay.wallet_service.entity.OutboxEvent;
import com.pulsepay.wallet_service.enums.OutboxEventStatus;
import com.pulsepay.wallet_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "pulsepay.outbox.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxPublisher {
    private final OutboxEventClaimService outboxEventClaimService;
// send event key and value to kafka.
    private final KafkaTemplate<String,String> kafkaTemplate;
   private final OutboxEventDispatcher outboxEventDispatcher;
   private final OutboxEventStatusService outboxEventStatusService;
   @Scheduled(fixedDelay = 1000)
   //run this method automatically then wait 1000 ms after it finishes before starting it again.
    public void publishPendingEvents(){
        List<OutboxEvent> events = outboxEventClaimService.claimPendingEvents();
        for(OutboxEvent outboxEvent : events) {
            EventDefinition definition = outboxEventDispatcher.dispatch(outboxEvent);
            //It asks dispatcher who handle this? Dispatcher replies TransferCompletedEventDefinition, great give me topic and key.
            String topic = definition.getTopic(outboxEvent);
            String key = definition.getKey(outboxEvent);
            //Before spring used to create kafka messages for us here we are creating by ourselves
            ProducerRecord<String,String> record = new ProducerRecord<>(topic,
                    key,
                    outboxEvent.getPayload());
            record.headers().add(
                    "event_id",
                    outboxEvent.getId().toString().getBytes(StandardCharsets.UTF_8)
                    );
            kafkaTemplate.send(record)
           .whenComplete((result, exception) -> {
                if (exception == null) {
                    outboxEventStatusService.markPublished(outboxEvent.getId());
                    log.info(
                            "Outbox event published successfully: eventId={}, eventType={}, topic={}",
                            outboxEvent.getId(),
                            outboxEvent.getEventType(),
                            topic
                    );

                } else {
                    log.error(
                            "Failed to publish outbox event: eventId={}, eventType={}, topic={}",
                            outboxEvent.getId(),
                            outboxEvent.getEventType(),
                            topic
                    );
                }
            });
        }

    }


}
