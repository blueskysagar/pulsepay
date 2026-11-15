package com.pulsepay.identity_service.security.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Holds JWT configuration supplied by the deployment environment.
 *
 * Keeping JWT configuration outside the token implementation prevents
 * cryptographic configuration from being hardcoded inside business logic.
 */
// Take configuration whose properties begin with prefix and map them into object
@ConfigurationProperties(prefix = "pulsepay.security.jwt")
public record JwtProperties( // RSA private key used by Identity Service to sign access tokens.
                             String privateKey,
                             // RSA public key used to verify access-token signatures.
                             String publicKey,
                                     // Identifies the service that issued the JWT.
                                     String issuer,
                                     // Lifetime of an access token in seconds.
                                     long accessTokenTtlSeconds)
{


}
