package com.pulsepay.identity_service.exception;
// number was parsed fine but the number was invalid
public class InvalidPhoneNumberException extends RuntimeException{
    public InvalidPhoneNumberException(String message){
        super(message);
    }
    // below one number cannot be parsed well so it throws below exception
    public InvalidPhoneNumberException(String message, Throwable cause){
        super(message,cause);
    }
}
