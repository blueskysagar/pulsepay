package com.pulsepay.identity_service.security.token.impl;

import com.pulsepay.identity_service.security.token.JwtProperties;
import com.pulsepay.identity_service.security.token.RsaKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Converts the configured PEM-encoded RSA private key
 * into the Java RSAPrivateKey required for JWT signing.
 */
@Component
@RequiredArgsConstructor
public class RsaKeyProviderImpl implements RsaKeyProvider {
    private final JwtProperties jwtProperties;
    @Override
    public RSAPrivateKey getPrivateKey() {
        //Read the RSAPrivateKey supplied through application configuration
        String pem = jwtProperties.privateKey();
        // Remove the PEM boundary markers and whitespace so that
        // only the Base64-encoded key material remains.
        String base64Key = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        //Right side is saying that go to Base64 toolbox, Grab text to bytes decoder tool and feed that into that tool so it produces raw binary bytes
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        try {
            //Basically it is wrapping the byteKey and attaching the label and saying treat this as PKCS8 formatted private key
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            // Walk to JavaSecurityWorkshop and fetch the specific "Key cutting machine" that knows how to handle RSA math meaning reading that number,
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            //Converting generic Private Key into specific RSA types
            // "Generate the PrivateKey object from the spec, and cast it to an RSAPrivateKey."
            // Because generatePrivate() returns the generic PrivateKey type, Java won't compile unless we cast it to its specific child type RSAPrivateKey:
            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            // The configured key cannot be converted into a usable RSA
            // private key, so application startup/runtime must fail clearly.
            throw new IllegalStateException(
                    "Unable to load RSA private key for JWT signing", ex
            );
        }
    }
        @Override
                public RSAPublicKey getPublicKey() {
            // Read the RSA public key supplied through application configuration.
            String pem = jwtProperties.publicKey();
            // Remove the PEM boundary markers and whitespace so that
            // only the Base64-encoded key material remains.
            String base64Key = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            // Decode the Base64 representation into the raw binary key bytes.
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            try {
                // Tell Java that these bytes represent an X.509 encoded public key.
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
                // Obtain the Java security factory capable of creating RSA keys.
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                // Convert the encoded key into Java's RSA public-key representation.
                return (RSAPublicKey) keyFactory.generatePublic(keySpec);

            } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
                // The configured public key cannot be converted into a usable RSA key.
                throw new IllegalStateException(
                        "Unable to load RSA public key for JWT verification",
                        ex
                );
            }
        }

        }
