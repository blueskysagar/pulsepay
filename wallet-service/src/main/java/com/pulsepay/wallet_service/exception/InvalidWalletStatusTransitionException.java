package com.pulsepay.wallet_service.exception;

import com.pulsepay.wallet_service.enums.WalletStatus;

public class InvalidWalletStatusTransitionException extends RuntimeException{
    public InvalidWalletStatusTransitionException(WalletStatus currentStatus, WalletStatus requestedStatus){
        super("Cannot change wallet status from " + currentStatus + " to " + requestedStatus);

    }
}
