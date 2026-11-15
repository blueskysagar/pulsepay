package com.pulsepay.payment_service.exception;

public class WalletBusinessException extends RuntimeException{
    public WalletBusinessException(String message){
        super(message);
    }
}
