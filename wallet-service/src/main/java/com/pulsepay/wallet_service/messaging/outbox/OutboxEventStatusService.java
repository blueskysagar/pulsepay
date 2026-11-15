package com.pulsepay.wallet_service.messaging.outbox;

import com.pulsepay.wallet_service.entity.OutboxEvent;
import com.pulsepay.wallet_service.enums.OutboxEventStatus;
import com.pulsepay.wallet_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
//"We use the event ID instead of passing the OutboxEvent object because the Kafka callback happens asynchronously, potentially after the original JPA persistence context/transaction has ended. Passing the ID lets us load the event again inside a fresh transaction."

@Service
@RequiredArgsConstructor
public class OutboxEventStatusService {
    private final OutboxEventRepository outboxEventRepository;
    @Transactional
    public void markPublished(UUID eventId){
        OutboxEvent outboxEvent =
                outboxEventRepository.findById(eventId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Outbox event not found: " + eventId
                                ));
        if(outboxEvent.getStatus() == OutboxEventStatus.PUBLISHED){
            return;
        }
        if(outboxEvent.getStatus() != OutboxEventStatus.PROCESSING){
            return;
        }
        outboxEvent.setStatus(OutboxEventStatus.PUBLISHED);
        outboxEvent.setPublishedAt(Instant.now());
        outboxEventRepository.save(outboxEvent);

    }




}
