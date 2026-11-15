package com.pulsepay.identity_service.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class PersistenceExceptionTranslator {
    //If postgres this specific constraint is violated we mean below thing happened.
    private static final String EMAIL_CONSTRAINT = "uk_identity_account_email";
    private static final String PHONE_CONSTRAINT = "uk_identity_account_phone";
    // Basically we are translating the ugly data base error that hibernate gives into an exception an application understands.
    public RuntimeException translate(DataIntegrityViolationException exception){
        //From database integrity error find the most specific underlying error and get its message.
        String message = exception.getMostSpecificCause().getMessage();
        if(message!=null && message.contains(EMAIL_CONSTRAINT)){
            return new DuplicateAccountException("An account already exists with this email.");
        }
        if(message!=null && message.contains(PHONE_CONSTRAINT)){
            return new DuplicateAccountException("An account already exists with this phone number.");
        }
        return exception;
    }

}
