package com.pulsepay.payment_service.security.token;

import java.security.interfaces.RSAPublicKey;
// That configured key is encrypted we need mechanism that can convert publicKeytext into usable RSApublicKey
public interface RsaKeyProvider {
    RSAPublicKey getPublicKey();
}
