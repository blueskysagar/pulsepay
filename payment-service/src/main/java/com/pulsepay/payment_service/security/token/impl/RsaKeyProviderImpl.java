package com.pulsepay.payment_service.security.token.impl;

import com.pulsepay.payment_service.security.token.JwtProperties;
import com.pulsepay.payment_service.security.token.RsaKeyProvider;
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
        String pem = jwtProperties.publicKey();
        String base64Key = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        //We convert human readable key to binary byte.
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        try{
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        //Java give me the factory capable of creating RSA keys
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        //Public Key is generated.

        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException ex){
        throw new IllegalStateException(
                "Unable to load RSA public key for JWT Verification",
                ex
        );
        }
    }

}
