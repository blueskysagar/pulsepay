package com.pulsepay.identity_service.security.token;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Provides the RSA private key used by Identity Service
 * when signing access tokens.
 *
 * The rest of the security layer should not need to know
 * how the private key is loaded or converted.
 */
public interface RsaKeyProvider {
    // Returns the RSA private key used for JWT signing.
    RSAPrivateKey getPrivateKey();
    // Returns the RSA public key used for JWT signature verification.
    RSAPublicKey getPublicKey();
}
