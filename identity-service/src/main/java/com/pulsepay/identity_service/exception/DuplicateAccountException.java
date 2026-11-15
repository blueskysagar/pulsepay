package com.pulsepay.identity_service.exception;

import lombok.Getter;

@Getter
public class DuplicateAccountException extends RuntimeException{
    private final ErrorCode errorCode;
    public DuplicateAccountException(String message){
        super(message);
        this.errorCode = ErrorCode.ACCOUNT_ALREADY_EXISTS;

    }
}
