package com.pulsepay.identity_service.security.token.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pulsepay.identity_service.entity.IdentityAccount;
import com.pulsepay.identity_service.security.token.AccessTokenService;
import com.pulsepay.identity_service.security.token.JwtProperties;
import com.pulsepay.identity_service.security.token.RsaKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import  java.util.Date;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AccessTokenServiceImpl implements AccessTokenService {
    private final JwtProperties jwtProperties;
    private final RsaKeyProvider rsaKeyProvider;
    @Override
    public String generateAccessToken(IdentityAccount account){
        //Capture the exact time at which access token was created.
        Instant issuedAt = Instant.now();
        //Calculate when this short lived token must expire
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.accessTokenTtlSeconds());
        //Define how JWT will be signed
        //RS256 means RSA signature with SHA-256
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .build();
        //Now we start building the claims carried by the access tokens
        JWTClaimsSet.Builder claimBuilder = new JWTClaimsSet.Builder();
        //Identify which Pulsepay account this access token represents
        claimBuilder.subject(
                account.getId().toString()
        );
        // Identify the trusted service that issued this access token.
        claimBuilder.issuer(
                jwtProperties.issuer()
        );
        // Record when the Identity Service created this access token.
        claimBuilder.issueTime(
                Date.from(issuedAt)
        );
        // Tell every token verifier exactly when this access token becomes invalid.
        claimBuilder.expirationTime(
                Date.from(expiresAt)
        );
        // Finish the claims object so it can be placed inside the JWT.
        JWTClaimsSet claims = claimBuilder.build();
        // Combine the signing instructions (header) and token data (claims)
// into a JWT that is ready to be cryptographically signed.
        SignedJWT signedJWT = new SignedJWT(header,claims);
        // Create an RSA signer using the private key owned by Identity Service.
        try{
            RSASSASigner signer = new RSASSASigner(
                rsaKeyProvider.getPrivateKey()
        );
            // Cryptographically sign the header and claims using
            // Identity Service's RSA private key and the RS256 algorithm.
            signedJWT.sign(signer);

        }catch (JOSEException | IllegalArgumentException ex){
            // Token creation is an internal security failure.
            // Do not expose cryptographic implementation details to the client.
            throw new IllegalStateException(
                    "Unable to generate access token",
                    ex
            );
        }
        // Convert the signed JWT object into the compact JWT string
// that will be returned to the caller.
        return signedJWT.serialize();


    }
    @Override
    public long getAccessTokenTtlSeconds(){
        // Expose the same configured lifetime used when creating the JWT
        // so the API response reports an accurate access-token lifetime.
        return jwtProperties.accessTokenTtlSeconds();
    }

}
