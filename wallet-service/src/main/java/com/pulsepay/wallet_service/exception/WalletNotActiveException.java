package com.pulsepay.wallet_service.exception;

import com.pulsepay.wallet_service.enums.WalletStatus;

public class WalletNotActiveException extends RuntimeException{
    public WalletNotActiveException(WalletStatus status){
        super("Wallet is not active. Current status: " + status);
    }
}
