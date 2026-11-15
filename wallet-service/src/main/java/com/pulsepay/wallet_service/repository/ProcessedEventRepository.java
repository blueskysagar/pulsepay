package com.pulsepay.wallet_service.repository;

import com.pulsepay.wallet_service.messaging.consumer.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    @Modifying
    @Query(value = """
           INSERT INTO processed_events (
                            id,
                       event_id,
                      event_type,
                     processed_at,
                       created_at
)
VALUES (
:id,
:eventId,
:eventType,
:processedAt,
:createdAt
)
ON CONFLICT (event_id) DO NOTHING
""", nativeQuery = true)
    //Try claim try to register this kafka event as processed
    //then data base tries to insert if it doesnt have else it rejects.
    int tryClaim(
            UUID id,
            UUID eventId,
            String eventType,
            Instant processedAt,
            Instant createdAt
    );
}
