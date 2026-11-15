package com.pulsepay.payment_service.exception;

public class IdempotencyKeyReuseException extends RuntimeException{
    public IdempotencyKeyReuseException(String message){
        super(message);
    }
}
