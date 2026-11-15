package com.pulsepay.wallet_service.security.token;

import java.security.interfaces.RSAPublicKey;

public interface RsaKeyProvider {
    /**
     * Returns the RSA public key used to verify
     * access-token signatures.
     *
     * Wallet Service only needs the public key because
     * it verifies tokens; it never signs them.
     */
    RSAPublicKey getPublicKey();
}
