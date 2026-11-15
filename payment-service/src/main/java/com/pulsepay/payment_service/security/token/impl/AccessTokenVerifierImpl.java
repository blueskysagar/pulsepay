package com.pulsepay.payment_service.security.token.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.crypto.impl.RSASSA;
import com.nimbusds.jwt.JWTClaimsSet;
import com.pulsepay.payment_service.security.token.AccessTokenVerifier;
import com.pulsepay.payment_service.security.token.JwtProperties;
import com.pulsepay.payment_service.security.token.RsaKeyProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.nimbusds.jwt.SignedJWT;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessTokenVerifierImpl implements AccessTokenVerifier {
    private final JwtProperties jwtProperties;
    private final RsaKeyProvider rsaKeyProvider;
    @Override
    public String verifyAndGetSubject(String token){
        try {
            //JWT let me open your envelope so that payment can see what is in header, what is in claims and signature
            SignedJWT signedJWT = SignedJWT.parse(token);
            // 2. Make sure the token uses the algorithm we expect.
            if (!JWSAlgorithm.RS256.equals(
                    signedJWT.getHeader().getAlgorithm()
            )) {
                throw new IllegalStateException(
                        "Invalid access token algorithm"
                );
            }
            //Get our trusted Public key
            RSAPublicKey publicKey =
                    rsaKeyProvider.getPublicKey();
            //Create a verifier using that public key
            RSASSAVerifier verifier = new RSASSAVerifier(publicKey);
            //Verify the cryptographic signature
            if (!signedJWT.verify(verifier)) {
                throw new IllegalStateException(
                        "Invalid access token signature"
                );

            }
            //We can inspect the claims
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            //Verify that identity service issued it
            if (!jwtProperties.issuer().equals(claims.getIssuer())) {
                throw new IllegalStateException(
                        "Invalid access token issuer"
                );
            }
            //Make sure expiration exists
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime == null) {
                throw new IllegalStateException(
                        "Access token does not contain an expiration time"
                );
            }
            //Make sure the token has not expired
            if (!expirationTime.toInstant().isAfter(Instant.now())) {
                throw new IllegalStateException(
                        "Access token has expired"
                );
            }
            //Get the subject
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new IllegalStateException(
                        "Access token does not contain a subject"
                );
            }
            //Our subject must be valid account UUID
            try {
                UUID.fromString(subject);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                        "Access token contains an invalid subject",
                        ex
                );
            }
            //Everything passed
            return subject;
        }catch (ParseException | JOSEException ex){
            throw new IllegalStateException(
                    "Unable to verify access token",
                    ex
            );
        }
    }

}
