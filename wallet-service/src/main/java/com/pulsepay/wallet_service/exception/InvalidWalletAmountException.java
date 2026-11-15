package com.pulsepay.wallet_service.exception;

import java.math.BigDecimal;

public class InvalidWalletAmountException extends RuntimeException{
    public InvalidWalletAmountException(BigDecimal amount){
        super("Wallet amount must be greater than zero. " + amount);
    }
}
