package com.pulsepay.payment_service.entity;

import com.pulsepay.payment_service.common.base.BaseEntity;
import com.pulsepay.payment_service.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends BaseEntity {
    @Column(name="source_wallet_id", nullable = false)
    private UUID sourceWalletId;
    @Column(name="destination_wallet_id", nullable = false)
    private UUID destinationWalletId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.CREATED;
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    public Payment(
            UUID sourceWalletId,
            UUID destinationWalletId,
            BigDecimal amount,
            String currency,
            String idempotencyKey
    ) {
        this.sourceWalletId = sourceWalletId;
        this.destinationWalletId = destinationWalletId;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.CREATED;
    }
    public void markProcessing(){
        if(this.status !=PaymentStatus.CREATED){
            throw new IllegalStateException("Payment cannot move to processing");

        }
        this.status=PaymentStatus.PROCESSING;
    }
    public void markCompleted() {
        if (this.status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Payment cannot move to COMPLETED");
        }

        this.status = PaymentStatus.COMPLETED;
    }

    public void markFailed() {
        if (this.status != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Payment cannot move to FAILED");
        }

        this.status = PaymentStatus.FAILED;
    }

}
