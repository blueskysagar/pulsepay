package com.pulsepay.payment_service.security.token;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
//We are creating this class because, we dont want five different service or class doing Security Contextholder.getContext().getAuthentication().credentials instead we create current Access Token
//Give me current request token
@Component

public class CurrentAccessToken {
    public String get(){
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();
        if(authentication==null){
            throw new IllegalStateException("No authenticated Request");
        }
        Object credentials = authentication.getCredentials();
        if(!(credentials instanceof String accessToken)||accessToken.isBlank()){
            throw new IllegalStateException(
                    "Authenticated request doesnot contain an access token"
            );
        }
        return accessToken;

        }

    }
