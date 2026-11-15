package com.pulsepay.wallet_service.enums;

public enum IdempotencyOperation {
    CREDIT,
    /*
     * Idempotency protection for wallet-to-wallet transfers.
     *
     * The same idempotency key can therefore be used for
     * different business operations without collision.
     */
    TRANSFER
}
