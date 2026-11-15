package com.pulsepay.identity_service.normalization;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EmailNormalizer {
    public String normalize(String email){
        return email.trim().toLowerCase(Locale.ROOT); // Locale.Root because no matter which country server you use just be consistent with lower case.
    }
}
