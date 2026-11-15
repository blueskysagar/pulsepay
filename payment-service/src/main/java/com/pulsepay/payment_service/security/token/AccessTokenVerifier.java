package com.pulsepay.payment_service.security.token;

public interface AccessTokenVerifier {
    String verifyAndGetSubject(String token);
}
