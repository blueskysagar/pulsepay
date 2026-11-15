package com.pulsepay.wallet_service.entity;

import com.pulsepay.wallet_service.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_completion_records")
@Getter
@Setter
@NoArgsConstructor
public class TransferCompletionRecord extends BaseEntity {
    @Id
    private UUID id;
    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "completed_at",nullable = false)
    private Instant completedAt;
    public TransferCompletionRecord(
            UUID transferId,
            UUID eventId,
            BigDecimal amount,
            String currency,
            Instant completedAt

    ){
        this.id=UUID.randomUUID();
        this.transferId=transferId;
        this.eventId=eventId;
        this.amount=amount;
        this.currency=currency;
        this.completedAt=completedAt;
    }



}
