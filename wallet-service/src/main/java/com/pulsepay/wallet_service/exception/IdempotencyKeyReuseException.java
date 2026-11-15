package com.pulsepay.wallet_service.exception;

public class IdempotencyKeyReuseException extends RuntimeException{
    public IdempotencyKeyReuseException(){
        super("The idempotency key was already used with a different request. ");
    }
}
