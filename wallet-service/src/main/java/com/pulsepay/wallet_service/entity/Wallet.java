package com.pulsepay.wallet_service.entity;

import com.pulsepay.wallet_service.common.base.BaseEntity;
import com.pulsepay.wallet_service.enums.WalletStatus;
import com.pulsepay.wallet_service.exception.InsufficientBalanceException;
import com.pulsepay.wallet_service.exception.InvalidWalletAmountException;
import com.pulsepay.wallet_service.exception.InvalidWalletStatusTransitionException;
import com.pulsepay.wallet_service.exception.WalletNotActiveException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents a single currency wallet owned by a PulsePay account.
 *
 * Example:
 *
 * Account A123
 *     |
 *     +---- AUD Wallet
 *     |
 *     +---- NPR Wallet
 *     |
 *     +---- USD Wallet
 *
 * The Wallet Service owns this data.
 */
@Entity
@Table(name = "wallet",
uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_wallet_account_currency",
                columnNames = {"account_id","currency"}
        )
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Wallet extends BaseEntity {
    @Id
    private UUID id;
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO; //When we create new wallet we want default balance zero
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletStatus status = WalletStatus.ACTIVE;

    /*
     * Generate the wallet ID in the application before the INSERT.
     *
     * We deliberately do not use GenerationType.IDENTITY because our
     * architecture uses application-generated UUIDs rather than
     * database-generated identity values.

     */
    @PrePersist
    protected void generateId() {
        if (id == null) {
            id = UUID.randomUUID();
        }


}
    /*
     * Change the wallet lifecycle state only when the requested transition
     * is explicitly allowed by the business rules.
     *
     * Keeping this rule inside the Wallet protects the entity from being
     * placed into an invalid lifecycle state by different application flows.
     */
    public void changeStatus(WalletStatus newStatus){
        if(newStatus==null){
            throw new IllegalArgumentException("Wallet Status cannot be null.");
        }
        if(status==WalletStatus.DEACTIVATED){
            throw new InvalidWalletStatusTransitionException(status, newStatus);
        }
        if(status==WalletStatus.ACTIVE){
            if(newStatus==WalletStatus.SUSPENDED || newStatus==WalletStatus.DEACTIVATED){
                status=newStatus;
                return;
            }

        }
        if (status == WalletStatus.SUSPENDED) {
            if (newStatus == WalletStatus.ACTIVE
                    || newStatus == WalletStatus.DEACTIVATED) {

                status = newStatus;
                return;
            }
        }
        if (status == newStatus) {
            return;
        }
        throw new InvalidWalletStatusTransitionException(
                status,
                newStatus
        );
    }
    /*
     * Increase the wallet balance by a valid positive amount.
     *
     * The Wallet owns this operation because changing balance is a
     * financial domain operation, not a generic field update.
     */
    public void credit(BigDecimal amount){
        /*
         * A null amount cannot represent a valid monetary operation.
         */
        if(amount==null || amount.signum() <=0){
            throw new InvalidWalletAmountException(amount);
        }
//        *
//     * Only active wallets can currently participate in financial
//     * operations.
//                *
//                * Suspended and deactivated wallets are intentionally blocked.
//     */
        if(this.status !=WalletStatus.ACTIVE){
            throw new WalletNotActiveException(this.status);

        }
        /*
         * BigDecimal performs decimal arithmetic suitable for monetary
         * values without the binary floating-point problems of double/float.
         */
        this.balance = this.balance.add(amount);
        // This whole thing of defining this in entity protects balance rules rather than doing get getBalance() and setBalance() which anybody can manipulate.
    }
    public void debit(BigDecimal amount){
//        / WHY: A wallet should protect its own balance invariants.
//        // The service coordinates the transfer, but Wallet controls
//        // how its balance can change.
        // WHY:
        // A wallet must never accept zero or negative money movement.
        if(amount==null || amount.compareTo(BigDecimal.ZERO) <=0){
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if(status!= WalletStatus.ACTIVE){
            // WHY: This is a business failure, not a generic Java state error.
            throw new WalletNotActiveException(status);

        }
        // WHY:
        // The wallet itself protects the "balance cannot become negative"
        // business invariant.
        if(balance.compareTo(amount)<0){
            throw new InsufficientBalanceException("Insufficient wallet balance");
        }
        balance=balance.subtract(amount);

    }

}
