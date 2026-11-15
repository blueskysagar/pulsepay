package com.pulsepay.identity_service.security.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
/**
 * Holds refresh-token and refresh-session security configuration
 * supplied by the deployment environment.
 *
 * This configuration is kept separate from JwtProperties because
 * refresh tokens are opaque credentials and are governed by
 * different security policies than JWT access tokens.
 */
/**
 * Maps automatically to 'pulsepay.security.refresh-token.long-ttl'
 */

@ConfigurationProperties(prefix = "pulsepay.security.refresh-token")

public record RefreshTokenProperties(
        /*
         * Maximum lifetime of a refresh session.
         *
         * The actual expiration timestamp is calculated when the
         * session is created and persisted as RefreshSession.expiresAt.
         */
        Duration ttl

){

}
