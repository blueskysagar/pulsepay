package com.pulsepay.wallet_service.entity;

import com.pulsepay.wallet_service.common.base.BaseEntity;
import com.pulsepay.wallet_service.enums.OutboxEventStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="outbox_events",
        indexes = {
        @Index(
                name="idx_outbox_event_status_created_at",
                columnList = "status,created_at" // our publisher might ask postgresql give me pending outbox events ordered by when they were created.
        )
        }

)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class OutboxEvent extends BaseEntity {
    @Id
    @GeneratedValue
    private UUID id;
    /* * WHAT: * Identifies the type of business object that produced the event. * * WHY: * The outbox table may eventually contain events from different * business operations, not only transfers. */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    /* * WHAT: * Describes what happened. * * Example: * TRANSFER_COMPLETED */
    @Column(name="event_type", nullable = false, length = 100)
    private String eventType;
    /* * WHAT: * Contains the event data as JSON text. * * WHY: * Kafka will eventually need the actual event information. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb") // jsonb converts string into binary json and write to disk, it validate the space, trailing zeros reject invalid sql.
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;
    @Column(name="published_at")
    private Instant publishedAt;
    @Column(name="claimed_at")
    private Instant claimedAt;




}
