package com.pulsepay.wallet_service.security.token.impl;

import com.pulsepay.wallet_service.security.token.JwtProperties;
import com.pulsepay.wallet_service.security.token.RsaKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class RsaKeyProviderImpl implements RsaKeyProvider {
    private final JwtProperties jwtProperties;
    @Override
    public RSAPublicKey getPublicKey(){
        /**
         * Returns the RSA public key used to verify
         * access-token signatures.
         *
         * Wallet Service only needs the public key because
         * it verifies tokens; it never signs them.
         */
        String pem = jwtProperties.publicKey();
        /*
         * PEM is a human-readable wrapper around Base64-encoded
         * binary key material.
         *
         * Java's key factory needs the actual Base64 content,
         * not the BEGIN/END markers.
         */
        String base64Key = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        /*
         * Convert Base64 text back into the raw binary bytes
         * representing the RSA public key.
         */
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        try{
            /*
             * Tell Java:
             *
             * "These bytes represent an X.509 encoded public key."
             */
            //But keySpec is still basically a description/container of the encoded key. It is not yet the actual Java RSAPublicKey object.
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            /*
             * Obtain Java's RSA key factory.
             *
             * Think:
             * "Give me the Java security component that understands RSA."
             */
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            /*
             * Convert the encoded key material into an actual
             * Java RSAPublicKey object that Nimbus can use
             * to verify JWT signatures.
             */
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        }catch (
                NoSuchAlgorithmException | InvalidKeySpecException ex
        ){
            /*
             * If the configured public key cannot be converted,
             * Wallet Service cannot safely verify JWTs.
             *
             * Fail clearly rather than silently accepting
             * unauthenticated requests.
             */
            throw new IllegalStateException("Unable to load RSA public key for JWT verification", ex);
        }


    }

}
