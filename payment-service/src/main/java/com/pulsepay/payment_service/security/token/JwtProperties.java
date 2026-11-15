package com.pulsepay.payment_service.security.token;

import org.springframework.boot.context.properties.ConfigurationProperties;
//It stores JWT settings that payment service need to verify access token.
@ConfigurationProperties(prefix = "pulsepay.security.jwt")
public record JwtProperties(String publicKey,
                            String issuer) {
}
