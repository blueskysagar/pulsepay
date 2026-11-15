package com.pulsepay.identity_service.security.token;

/**
 * Verifies the cryptographic integrity and registered claims
 * of PulsePay access tokens.
 */
public interface AccessTokenVerifier {
    /**
     * Verifies that the supplied JWT is a valid PulsePay access token.
     *
     * @param token compact serialized JWT
     * @return the authenticated account identifier stored in the subject claim
     */
    String verifyAndGetSubject(String token);

}
