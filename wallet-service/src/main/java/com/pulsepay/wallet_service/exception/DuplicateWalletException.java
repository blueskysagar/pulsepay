package com.pulsepay.wallet_service.exception;


import java.util.UUID;


public class DuplicateWalletException extends RuntimeException{
    private final WalletErrorCode errorCode;
    /*
     * The exception carries both:
     *
     * 1. A stable machine-readable error code.
     * 2. A human-readable message.
     *
     * The API client can safely depend on the error code while
     * the message can remain descriptive for humans.
     */

    public DuplicateWalletException() {
        super("A wallet already exists for this account and currency.");
        this.errorCode = WalletErrorCode.WALLET_ALREADY_EXISTS;
    }
    public DuplicateWalletException(UUID accountId, String currency){
        super(
                "A wallet already exists for account "
                        + accountId
                        + " and currency "
                        + currency
        );
        this.errorCode = WalletErrorCode.WALLET_ALREADY_EXISTS;
    }
    public WalletErrorCode getErrorCode(){
        return errorCode;
    }
}
