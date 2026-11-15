package com.pulsepay.wallet_service.security.token;
/**
 * Defines the security contract for verifying PulsePay access tokens.
 *
 * WHY:
 *
 * The JWT filter should not know how JWT cryptography works.
 * It only needs one simple answer:
 *
 * "Is this token valid, and which account does it represent?"
 *
 * Keeping this behind an interface separates:
 *
 * JWT request handling
 *        from
 * JWT verification implementation.
 */
public interface AccessTokenVerifier {
    /**
     * Verifies the supplied JWT and returns the authenticated
     * account identifier stored in the token's subject claim.
     *
     * The returned subject can only be trusted when all required
     * JWT security checks have successfully passed.
     *
     * @param token compact serialized JWT received from the
     *              Authorization: Bearer <token> header
     * @return trusted PulsePay account ID
     * @throws IllegalStateException when the token is invalid.
     */
    String verifyAndGetSubject(String token);

}
