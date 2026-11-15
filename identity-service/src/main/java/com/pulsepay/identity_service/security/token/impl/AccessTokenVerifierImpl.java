package com.pulsepay.identity_service.security.token.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pulsepay.identity_service.security.token.AccessTokenVerifier;
import com.pulsepay.identity_service.security.token.JwtProperties;
import com.pulsepay.identity_service.security.token.RsaKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    public String verifyAndGetSubject(String token) {
        try {
            // Parse the compact JWT string into Nimbus's SignedJWT representation.
            SignedJWT signedJWT = SignedJWT.parse(token);
            // The access-token verifier only accepts tokens signed
            // using the algorithm our Identity Service expects.
            if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
                throw new IllegalStateException("Invalid access-token algorithm");
            }
            // Create an RSA verifier using the PUBLIC key.
            RSASSAVerifier verifier = new RSASSAVerifier(rsaKeyProvider.getPublicKey());
            // Verify that the JWT signature was produced by the
            // corresponding RSA private key.
            if (!signedJWT.verify(verifier)) {
                throw new IllegalStateException("Invalid access token signature");
            }
            // Extract the claims only after the cryptographic signature
            // has been successfully verified.
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            // Verify that this JWT was issued by our trusted Identity Service.
            if (!jwtProperties.issuer().equals(claims.getIssuer())) {
                throw new IllegalStateException("Invalid access-token issuer");
            }
            // Read the expiration timestamp contained in the verified JWT.
            Date expirationTime = claims.getExpirationTime();
            // Every access token must contain an expiration timestamp.
            if (expirationTime == null) {
                throw new IllegalStateException("Access token does not contain an expiration time");

            }
            // Reject tokens whose expiration time has already passed.
            if (!expirationTime.toInstant().isAfter(Instant.now())) {
                throw new IllegalStateException("Access token has expired");

            }
            // Read the account identifier from the verified subject claim
            String subject = claims.getSubject();
            // Every authenticated access token must identify an account.
            if (subject == null || subject.isBlank()) {
                throw new IllegalStateException("Access token doesnot contain a subject");
            }
            try {
                /*
                 * IdentityAccount identifiers are UUIDs.
                 *
                 * The JWT subject is therefore not merely required to exist;
                 * it must also conform to the identifier format used by our domain.
                 */
                UUID.fromString(subject);
            } catch (IllegalArgumentException ex) {
                /*
                 * Do not allow an arbitrary string to become
                 * the authenticated principal.
                 */
                throw new IllegalStateException(
                        "Access token contains an invalid subject",
                        ex
                );
            }
                /*
                 * At this point:
                 *
                 * - JWT structure is valid
                 * - algorithm is RS256
                 * - RSA signature is valid
                 * - issuer is trusted
                 * - expiration exists
                 * - token is not expired
                 * - subject exists
                 * - subject is a valid PulsePay account UUID
                 *
                 * The subject can now be trusted as the account identity.
                 */
                return subject;


        } catch (ParseException | JOSEException ex) {
            // Do not expose parsing or cryptographic implementation details
            // to the client. The token is simply treated as invalid.
            throw new IllegalStateException(
                    "Unable to verify access token",
                    ex
            );
        }


    }


    }
