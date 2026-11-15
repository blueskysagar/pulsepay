package com.pulsepay.wallet_service.validation;

import com.pulsepay.wallet_service.enums.SupportedCurrency;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class CurrencyValidator {
    /*
     * Verifies that the normalized currency is one of the
     * currencies currently supported by PulsePay.
     *
     * The input is expected to already be normalized.
     * Therefore values such as "aud" should have become
     * "AUD" before reaching this validator.
     */
    public boolean isSupported(String currency){
        return Arrays.stream(SupportedCurrency.values())
                .anyMatch(
                        supportedCurrency -> supportedCurrency.name().equals(currency)
                );
    }
}
