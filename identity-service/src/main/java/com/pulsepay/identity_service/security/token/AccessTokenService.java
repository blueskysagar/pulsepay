package com.pulsepay.identity_service.security.token;

import com.pulsepay.identity_service.entity.IdentityAccount;

public interface AccessTokenService {

    // Creates a short-lived access token representing an account
    // that has already successfully completed authentication.
    String generateAccessToken(IdentityAccount account);
    // Returns the configured lifetime of access tokens so the authentication
    // response can tell the client how long the issued access token is valid.
    long getAccessTokenTtlSeconds();


}
