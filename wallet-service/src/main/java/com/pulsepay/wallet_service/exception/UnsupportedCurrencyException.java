package com.pulsepay.wallet_service.exception;

public class UnsupportedCurrencyException extends RuntimeException{
    public UnsupportedCurrencyException(String currency){
        super("Unsupported currency: " + currency);
    }

}
