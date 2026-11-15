package com.pulsepay.identity_service.exception;

public class InvalidCredentialsException extends RuntimeException{
    public InvalidCredentialsException(){
        super("Invalid credentials");
    }
}
