package com.pulsepay.wallet_service.entity;

import com.pulsepay.wallet_service.common.base.BaseEntity;
import com.pulsepay.wallet_service.enums.IdempotencyOperation;
import com.pulsepay.wallet_service.enums.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name="idempotency_record", uniqueConstraints = {
        @UniqueConstraint(name = "uk_idempotency_account_key_operation",
                          columnNames = {
                             "account_id",
                                  "idempotency_key",
                                  "operation"

                          }


        )
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class IdempotencyRecord extends BaseEntity {
    @Id
    private UUID id;
    /*
     * The idempotency key belongs to the account that supplied it.
     *
     * Including account_id in the uniqueness boundary prevents one
     * account's idempotency key from interfering with another account.
     */
    @Column(name="account_id", nullable = false)
    private UUID accountId;
    /*
     * This key identifies one logical client operation.
     *
     * The client must reuse this exact key when retrying the same
     * operation.
     */
    @Column(name="idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    /*
     * Different operations can legitimately use the same key.
     *
     * Therefore the operation participates in the unique constraint.
     */
    @Enumerated(EnumType.STRING)
    @Column(name="operation", nullable = false, length = 30)
    private IdempotencyOperation operation;
    /*
     * Allows us to detect reuse of the same key with a different
     * request payload.
     */
    @Column(name="request_fingerprint",nullable = false, length = 64)
    private String requestFingerprint;
    /*
     * Tracks whether the logical operation is currently executing
     * or has completed.
     */
    @Enumerated(EnumType.STRING)
    private IdempotencyStatus status;
    @PrePersist
    protected void generateId(){
        if(id==null){
            id = UUID.randomUUID();
        }
    }
    @Column(name = "resource_id")
    private UUID resourceId;

}
