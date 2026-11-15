package com.pulsepay.wallet_service.normalization;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class CurrencyNormalizer {
    /*
            * Currency codes follow the ISO 4217 three-letter format.
            *
            * We normalize the value to uppercase so that logically
     * identical values such as:
            *
            * "aud"
            * "Aud"
            * "AUD"
            *
            * are treated consistently by the application.
            */
    public String normalize(String currency){
        if(currency==null){
            return null;
        }
        return currency.trim().toUpperCase(Locale.ROOT); //trim removes all the spaces. Locale.ROOT forces computer to ignore the language rules.
    }

}
