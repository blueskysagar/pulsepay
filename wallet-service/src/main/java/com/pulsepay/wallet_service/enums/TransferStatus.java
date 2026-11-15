package com.pulsepay.wallet_service.enums;

public enum TransferStatus {
    /**
     * Transfer has been created but has not reached its final state yet.
     */
    PENDING,
    /**
     * Source debit and destination credit completed successfully.
     */
    COMPLETED,
    /**
     * Transfer could not be completed successfully.
     */
    FAILED
}
