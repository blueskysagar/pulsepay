package com.pulsepay.wallet_service.security.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Holds JWT verification configuration supplied by the deployment environment.
 *
 * Wallet Service does NOT create access tokens.
 * It only verifies tokens created by Identity Service.
 *
 * Therefore Wallet Service primarily needs:
 *
 * - the RSA public key
 * - the trusted issuer
 *
 * Keeping these values outside the verification code prevents
 * security configuration from being hardcoded inside business logic.
 */
//Take all configuration values under pulsepay.security.jwt and put them into this Java class/record.”
@ConfigurationProperties(prefix="pulsepay.security.jwt")
public record JwtProperties(
//        / Public RSA key used by Wallet Service to verify
        // access-token signatures created by Identity Service.
        String publicKey,
        // Identifies the trusted service that issued the JWT.
        String issuer
){

}
