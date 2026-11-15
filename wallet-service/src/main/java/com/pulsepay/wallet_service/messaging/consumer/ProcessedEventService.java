package com.pulsepay.wallet_service.messaging.consumer;

import com.pulsepay.wallet_service.dto.event.TransferCompletedEvent;
import com.pulsepay.wallet_service.entity.TransferCompletionRecord;
import com.pulsepay.wallet_service.repository.ProcessedEventRepository;
import com.pulsepay.wallet_service.repository.TransferCompletionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProcessedEventService {
    private final ProcessedEventRepository processedEventRepository;
    private final TransferCompletionRecordRepository transferCompletionRecordRepository;
    @Transactional
    public boolean process(
            UUID eventId,
            String eventType,
            TransferCompletedEvent event

    ){
        Instant now = Instant.now();
        int inserted = processedEventRepository.tryClaim(
                UUID.randomUUID(),
                eventId,
                eventType,
                now,
                now
        );
        if(inserted==0){
            return false;
        }
        TransferCompletionRecord completionRecord = new TransferCompletionRecord(
                event.transferId(),
                eventId,
                event.amount(),
                event.currency(),
                now
        );
        transferCompletionRecordRepository.save(completionRecord);
        return true;

    }
}
