package com.pulsepay.wallet_service.exception;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException{
    public WalletNotFoundException(UUID walletId){
        super("Wallet not found: " + walletId);
    }
}
