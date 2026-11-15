package com.pulsepay.identity_service.normalization;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.pulsepay.identity_service.exception.InvalidPhoneNumberException;
import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {
    //PhoneNumberUtil is the class which helps us in parsing and validating all international numbers
    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    //Converts the number into E.164 representation. meaning it will convert number like 9845066065 into +9779845066065
    public String normalize(String phoneNumber){
        try{
            //Parse the raw number into structured format
            var number = phoneNumberUtil.parse(phoneNumber,null);
            //Parsing only tells whether an input can be interpreted but doesnot validate if its valid number
            if(!phoneNumberUtil.isValidNumber(number)){
                throw new InvalidPhoneNumberException("Invalid phone number");

            }
            //Store phone number in one canonical format
            //Example +977 like this
            return phoneNumberUtil.format(
                    number,
                    PhoneNumberUtil.PhoneNumberFormat.E164);

        }catch(NumberParseException ex){
            //Convert third party library exception into our application domain specific exception
            throw new InvalidPhoneNumberException(
                    "Invalid phone number",
                    ex
            );
        }

    }



}
