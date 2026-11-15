package com.pulsepay.wallet_service.messaging.outbox;

import com.pulsepay.wallet_service.entity.OutboxEvent;
import com.pulsepay.wallet_service.enums.OutboxEventStatus;
import com.pulsepay.wallet_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxEventClaimService {
    private final OutboxEventRepository outboxEventRepository;
    @Transactional
    public List<OutboxEvent> claimPendingEvents(){
        List<OutboxEvent> events = outboxEventRepository.findPendingEventsForUpdate();
        for(OutboxEvent outboxEvent : events){
            outboxEvent.setStatus(OutboxEventStatus.PROCESSING);
            outboxEvent.setClaimedAt(Instant.now());
        }
        outboxEventRepository.saveAll(events);

        return events;
    }
}
