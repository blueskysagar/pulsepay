package com.pulsepay.wallet_service.exception;

public class IdempotencyRequestAlreadyClaimedException extends RuntimeException{
    public IdempotencyRequestAlreadyClaimedException(){
        super("This idempotency request has already been claimed. ");
    }
}
