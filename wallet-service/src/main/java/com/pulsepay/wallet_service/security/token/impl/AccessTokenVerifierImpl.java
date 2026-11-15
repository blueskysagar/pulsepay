package com.pulsepay.wallet_service.security.token.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pulsepay.wallet_service.security.token.AccessTokenVerifier;
import com.pulsepay.wallet_service.security.token.JwtProperties;
import com.pulsepay.wallet_service.security.token.RsaKeyProvider;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.internal.database.DatabaseExecutionStrategy;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Verifies PulsePay access tokens issued by Identity Service.
 *
 * SECURITY RESPONSIBILITY:
 *
 * Identity Service creates the JWT using its RSA PRIVATE key.
 *
 * Wallet Service does NOT create JWTs.
 * Wallet Service only verifies them using the RSA PUBLIC key.
 *
 * The verification pipeline is:
 *
 * JWT
 *  ↓
 * Parse
 *  ↓
 * Verify algorithm
 *  ↓
 * Verify RSA signature
 *  ↓
 * Verify issuer
 *  ↓
 * Verify expiration
 *  ↓
 * Verify subject
 *  ↓
 * Verify subject is a UUID
 *  ↓
 * Return trusted account ID
 */
@Service
@RequiredArgsConstructor
public class AccessTokenVerifierImpl implements AccessTokenVerifier {
    private final JwtProperties jwtProperties;
    private final RsaKeyProvider rsaKeyProvider;
    @Override
    public String verifyAndGetSubject(String token){
        try{
            /*
             * STEP 1 — PARSE
             *
             * Convert the compact JWT String into Nimbus's
             * SignedJWT representation.
             *
             * IMPORTANT:
             *
             * Parsing only means:
             * "This string has a JWT structure that Nimbus
             * can understand."
             *
             * Parsing does NOT mean the token is trustworthy.
             */
            SignedJWT signedJWT = SignedJWT.parse(token);
            /*
             * STEP 2 — VERIFY EXPECTED ALGORITHM
             *
             * PulsePay access tokens are created using RS256.
             *
             * We explicitly reject tokens using another
             * signing algorithm.
             */
            if(!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())){
                throw new IllegalStateException(
                        "Invalid access-token algorithm"
                );
            }
            /*
             * STEP 3 — CREATE RSA VERIFIER
             *
             * Identity Service signs the JWT with:
             *
             *       RSA PRIVATE KEY
             *
             * Wallet Service verifies it with:
             *
             *       RSA PUBLIC KEY
             *
             * The public key comes from our dedicated
             * RsaKeyProvider rather than being hardcoded here.
             */
            RSASSAVerifier verifier = new RSASSAVerifier(rsaKeyProvider.getPublicKey());
            /*
             * STEP 4 — VERIFY CRYPTOGRAPHIC SIGNATURE
             *
             * This is the critical trust check.
             *
             * We are asking:
             *
             * "Was this JWT signed using the private RSA key
             * corresponding to our trusted public key?"
             *
             * If somebody modifies the JWT payload, the
             * signature will no longer match.
             */
            if(!signedJWT.verify(verifier))
            {
                throw new IllegalStateException("Invalid access token signature");
            }
            /*
             * Only AFTER successful signature verification
             * do we trust the claims carried by this JWT.
             *
             * Before verification, claims are simply
             * untrusted client-controlled data.
             */
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            /*
             * STEP 5 — VERIFY ISSUER
             *
             * A token can be cryptographically valid but still
             * come from an issuer that Wallet Service does not trust.
             *
             * PulsePay Wallet Service only trusts tokens issued
             * by the configured PulsePay Identity Service.
             */
            if(!jwtProperties.issuer().equals(claims.getIssuer())){
                throw new IllegalStateException("Invalid access token issuer");
            }
            /*
             * STEP 5 — VERIFY ISSUER
             *
             * A token can be cryptographically valid but still
             * come from an issuer that Wallet Service does not trust.
             *
             * PulsePay Wallet Service only trusts tokens issued
             * by the configured PulsePay Identity Service.
             */
            Date expirationTime = claims.getExpirationTime();
            if(expirationTime==null){
                throw new IllegalStateException("Access token doesnot contain an expiration time");
            }
            /*
             * STEP 7 — REJECT EXPIRED TOKEN
             *
             * The token is valid only while:
             *
             * expiration time > current time
             */
            if(!expirationTime.toInstant().isAfter(Instant.now())){
                throw new IllegalStateException("Access token has expired");
            }
            /*
             * STEP 8 — EXTRACT SUBJECT
             *
             * Identity Service puts the IdentityAccount UUID
             * into the JWT subject claim.
             *
             * Therefore:
             *
             * JWT subject → PulsePay account ID
             */
            String subject = claims.getSubject();
            /*
             * Every authenticated access token must identify
             * an account.
             */
            if(subject==null || subject.isBlank()){
                throw new IllegalStateException("Access token doesnot contain a subject");
            }
            /*
             * STEP 9 — VALIDATE DOMAIN FORMAT
             *
             * Our IdentityAccount IDs are UUIDs.
             *
             * We therefore don't merely check that the subject
             * exists; we verify that it is actually a UUID.
             */
            try{
                UUID.fromString(subject);
            } catch (IllegalArgumentException ex){
                throw new IllegalStateException("Access token contains an invalid subject",ex);

            }
            /*
             * At this point every required trust check has passed:
             *
             * ✓ JWT can be parsed
             * ✓ algorithm is RS256
             * ✓ RSA signature is valid
             * ✓ issuer is trusted
             * ✓ expiration exists
             * ✓ token is not expired
             * ✓ subject exists
             * ✓ subject is a valid UUID
             *
             * Therefore the subject can now safely become
             * the authenticated account identity.
             */
            return subject;


        }catch (ParseException | JOSEException ex) {
            /*
             * JWT parsing and cryptographic verification failed.
             *
             * Do not expose low-level parsing or cryptographic
             * details to the client.
             *
             * From the API's perspective:
             *
             * "This token cannot be trusted."
             */
            throw new IllegalStateException("Unable to verify access token", ex);
        }

        }
    }
