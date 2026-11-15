package com.pulsepay.payment_service.exception;
//We could not obtain reliable result from wallet so we introduce this exception
public class WalletCommunicationException extends RuntimeException{
    public WalletCommunicationException(String message, Throwable cause){
        super(message,cause);
    }
}
