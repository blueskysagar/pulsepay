package com.pulsepay.wallet_service.messaging.outbox;

import com.pulsepay.wallet_service.config.OutboxRecoveryProperties;
import com.pulsepay.wallet_service.entity.OutboxEvent;
import com.pulsepay.wallet_service.enums.OutboxEventStatus;
import com.pulsepay.wallet_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

//FInd the processing events that has been stuck too long and bring it back to normal so that it becomes available
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "pulsepay.outbox.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)

public class OutboxEventRecoveryService {
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxRecoveryProperties outboxRecoveryProperties;
    @Scheduled(fixedDelayString = "${pulsepay.outbox.recovery.interval}")
    @Transactional
    public void recoverStaleEvents(){
        Instant cutoff =
                Instant.now()
                        .minus(outboxRecoveryProperties.staleAfter());
        List<OutboxEvent> events = outboxEventRepository.findStaleProcessingEvents(cutoff);
        for(OutboxEvent outboxEvent : events){
            outboxEvent.setStatus(OutboxEventStatus.PENDING);
            outboxEvent.setClaimedAt(null);
        }
        outboxEventRepository.saveAll(events);
    }
}
