package com.pulsepay.wallet_service.entity;

import com.pulsepay.wallet_service.common.base.BaseEntity;
import com.pulsepay.wallet_service.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="transfers")
@Getter
//@Setter
@NoArgsConstructor

public class Transfer extends BaseEntity {
    /**
     * Unique identity of this transfer.
     *
     * WHY:
     * Every transfer needs its own durable identifier so that
     * we can reference it in API responses, logs, support cases,
     * reconciliation, and future events.
     */
    @Id
    private UUID id;
    /**
     * The wallet from which money is being removed.
     *
     * WHY UUID instead of @ManyToOne Wallet:
     * We intentionally keep the transfer dependent on the wallet's
     * identity rather than creating a tight JPA object relationship.
     * This keeps the domain boundary cleaner and is more compatible
     * with our future microservice architecture.
     */
    @Column(name="source_wallet_id", nullable = false)
    private UUID sourceWalletId;
    /**
     * The wallet receiving the money.
     *
     * WHY:
     * A transfer must explicitly record both sides of the movement.
     */
    @Column(name="destination_wallet_id", nullable = false)
    private UUID destinationWalletId;
    /**
     * Amount of money transferred.
     *
     * WHY BigDecimal:
     * Money must not use double/float because binary floating-point
     * representation can introduce precision errors.
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    /**
     * Currency of the transfer, for example USD or AUD.
     *
     * WHY store currency on the transfer:
     * A transfer is a historical record. We should know which currency
     * was involved without having to reconstruct it from current wallet
     * state later.
     */
    @Column(nullable = false, length=3)
    private String currency;
    /**
     * Current lifecycle state of the transfer.
     *
     * WHY EnumType.STRING:
     * We store "COMPLETED" instead of numeric ordinal values such as 1.
     * Numeric enum storage is dangerous because changing enum ordering
     * can change the meaning of existing database records.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private TransferStatus status;

    /**
     * Constructor used by the application/service layer when creating
     * a new transfer.
     *
     * WHY:
     * We don't expose arbitrary setters for financial state.
     * The entity should be created in a controlled state.
     */
    public Transfer(
            UUID sourceWalletId,
            UUID destinationWalletId,
            BigDecimal amount,
            String currency
    ){
        this.id = UUID.randomUUID();
        this.sourceWalletId = sourceWalletId;
        this.destinationWalletId = destinationWalletId;
        this.amount=amount;
        this.currency=currency;
        this.status = TransferStatus.PENDING;
    }
    /**
     * Marks the transfer as successfully completed.
     *
     * WHY:
     * Instead of allowing callers to directly manipulate the status,
     * the domain exposes an operation representing the business action.
     */
    public void markCompleted(){
        this.status = TransferStatus.COMPLETED;
    }
    /**
     * Marks the transfer as failed.
     *
     * WHY:
     * The entity owns its lifecycle transition rather than exposing
     * a public setter that would allow arbitrary status changes.
     */
    public void markFailed(){
        this.status = TransferStatus.FAILED;
    }


}
