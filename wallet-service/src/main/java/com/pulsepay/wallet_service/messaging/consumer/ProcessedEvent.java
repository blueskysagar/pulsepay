package com.pulsepay.wallet_service.messaging.consumer;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Table(name = "processed_events")

public class ProcessedEvent  {
    @Id
    private UUID id;
    @Column(name="event_id", nullable = false, unique = true)
    private UUID eventId;
    @Column(name="event_type",nullable = false, length=100)
    private String eventType;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    protected ProcessedEvent() {
    }
    public ProcessedEvent(
            UUID id,
            UUID eventId,
            String eventType,
            Instant processedAt,
            Instant createdAt

    ){
        this.id=id;
        this.eventId=eventId;
        this.eventType=eventType;
        this.processedAt=processedAt;
        this.createdAt=createdAt;
    }
}
