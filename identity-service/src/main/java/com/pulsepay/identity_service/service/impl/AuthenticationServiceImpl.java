package com.pulsepay.identity_service.service.impl;

import com.pulsepay.identity_service.dto.request.LoginRequest;
import com.pulsepay.identity_service.dto.request.RefreshTokenRequest;
import com.pulsepay.identity_service.dto.response.LoginResponse;
import com.pulsepay.identity_service.entity.IdentityAccount;
import com.pulsepay.identity_service.entity.RefreshSession;
import com.pulsepay.identity_service.enums.AccountStatus;
import com.pulsepay.identity_service.exception.InvalidCredentialsException;
import com.pulsepay.identity_service.normalization.EmailNormalizer;
import com.pulsepay.identity_service.repository.IdentityAccountRepository;
import com.pulsepay.identity_service.repository.RefreshSessionRepository;
import com.pulsepay.identity_service.security.token.AccessTokenService;
import com.pulsepay.identity_service.security.token.RefreshTokenProperties;
import com.pulsepay.identity_service.security.token.RefreshTokenService;
import com.pulsepay.identity_service.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {
    private final IdentityAccountRepository identityAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailNormalizer emailNormalizer;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenProperties refreshTokenProperties;
    private final RefreshSessionRepository refreshSessionRepository;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedEmail = emailNormalizer.normalize(request.getEmail());
        IdentityAccount account = identityAccountRepository.findByEmail(normalizedEmail).orElseThrow(InvalidCredentialsException::new);
        // Compare the raw password supplied during login against
        // the Argon2 password hash stored in the database.
        boolean passwordMatches =
                passwordEncoder.matches(request.getPassword(), account.getPasswordHash());
        // Never reveal whether the email or password was incorrect.
        // Both cases result in the same external authentication failure.
        if (!passwordMatches) {
            throw new InvalidCredentialsException();
        }
        // Authentication credentials are valid, but the account must also
// be in an allowed lifecycle state before we issue authentication tokens.
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
        // Authentication has succeeded.
        // Generate a new short-lived access token representing
        // this authenticated PulsePay account.
        String accessToken = accessTokenService.generateAccessToken(account);
        // Generate a new cryptographically secure opaque refresh token for this authenticated session.
        String refreshToken = refreshTokenService.generateRefreshToken();
        // Hash the refresh token before any persistence so the raw bearer credential is never stored in PostgreSQL.
        String refreshTokenHash = refreshTokenService.hashRefreshToken(refreshToken);

        // Calculate the concrete expiration timestamp from the configured
// refresh-session lifetime rather than hardcoding a fixed duration in the service.
        Instant expiresAt = Instant.now().plus(refreshTokenProperties.ttl());
        // Build the durable refresh session using the account identity and hashed credential.
// The raw refresh token is intentionally never placed in the persistence entity.
        RefreshSession refreshSession = RefreshSession.builder()
                .identityAccount(account)
                .tokenHash(refreshTokenHash)
                .expiresAt(expiresAt)
                .build();
        // Persist the refresh session so the server retains authoritative state
// for future refresh-token validation, rotation, and revocation.
        refreshSessionRepository.save(refreshSession);

// Return the raw refresh token only to the authenticated client.
// The database contains only its SHA-256 hash.
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenService.getAccessTokenTtlSeconds())
                .tokenType("Bearer") // Identify the authentication scheme the client must use when presenting
// the access token in the HTTP Authorization header.
                .build();


    }

    @Override
    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        // Extract the raw refresh credential supplied by the client.
// The raw token exists only in memory and will never be persisted.
        String refreshToken = request.getRefreshToken();
        // Hash the incoming refresh credential so it can be matched against
// the SHA-256 hash stored in the persistent refresh-session record.
        String refreshTokenHash = refreshTokenService.hashRefreshToken(refreshToken);
        // Locate the persistent refresh session associated with this credential.
// If no session exists, the token is not a valid active authentication credential.
        RefreshSession refreshSession =
                refreshSessionRepository.findByTokenHashForUpdate(refreshTokenHash)
                        .orElseThrow(InvalidCredentialsException::new);
        // A revoked refresh session must never be used to obtain new credentials,
// even if its natural expiration time has not yet been reached.
        if (refreshSession.getRevokedAt() != null) {
            throw new InvalidCredentialsException();
        }
        // The refresh session has a server-controlled lifetime.
// Once its expiration timestamp has been reached, it can no longer
// be used to obtain new access or refresh credentials.
        if (!refreshSession.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidCredentialsException();
        }
// Retrieve the account that owns this authenticated refresh session.
// The session-account relationship is the authoritative link between
// the refresh credential and the PulsePay identity.
        IdentityAccount account = refreshSession.getIdentityAccount();
        // Re-check the current account lifecycle state before issuing new credentials.
// A valid refresh session must not bypass an account suspension or deactivation.
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
// The refresh session has passed all authentication checks,
// so issue a new short-lived access token for the authenticated account
        String accessToken = accessTokenService.generateAccessToken(account);
// Generate a replacement refresh token so the current refresh credential
// is rotated and cannot be reused indefinitely.
        String newRefreshToken = refreshTokenService.generateRefreshToken();
// Hash the replacement refresh credential before persistence.
// Only the SHA-256 hash is allowed to enter the database.
        String newRefreshTokenHash = refreshTokenService.hashRefreshToken(newRefreshToken);
        //Rotate the persistent refresh credential by replacing the old token hash
// with the hash of the newly generated refresh token.
// The previous raw token can no longer authenticate this session.
        refreshSession.setTokenHash(newRefreshTokenHash);
// Record the successful refresh operation so the session retains
// the most recent time at which its credential was used.
        refreshSession.setLastUsedAt(Instant.now());
//Persist the rotated refresh credential and successful usage timestamp.
// The transaction ensures these changes are committed together.
        refreshSessionRepository.save(refreshSession);
// Return the newly issued credentials to the client.
// The raw refresh token is returned only here; its hash remains server-side.
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .accessTokenExpiresIn(accessTokenService.getAccessTokenTtlSeconds())
                .tokenType("Bearer")
                .build();
    }

    @Override
    @Transactional
    public void logout(
            RefreshTokenRequest request,
            String authenticatedAccountId
    ) {

        /*
         * The authenticated account ID came from the verified JWT.
         *
         * JwtAuthenticationFilter verified the access token and placed
         * its subject into Spring Security's Authentication object.
         *
         * The controller extracted that identity and passed it here.
         */
        UUID accountId;

        try {
            /*
             * Our persistence model uses UUID for IdentityAccount.id,
             * while the JWT subject is represented as a String.
             *
             * Convert the trusted identity into the persistence type.
             */
            accountId = UUID.fromString(authenticatedAccountId);

        } catch (IllegalArgumentException ex) {

            /*
             * The authenticated identity cannot be represented as a
             * valid PulsePay account UUID.
             *
             * Treat the authentication context as unusable rather than
             * exposing parsing details to the client.
             */
            throw new InvalidCredentialsException();
        }

        /*
         * Hash the raw refresh token supplied by the client.
         *
         * The raw refresh token remains only in memory and is never
         * persisted to PostgreSQL.
         */
        String refreshTokenHash =
                refreshTokenService.hashRefreshToken(
                        request.getRefreshToken()
                );

        /*
         * Find the refresh session using BOTH:
         *
         * 1. The supplied refresh-token hash.
         * 2. The authenticated account ID from the verified JWT.
         *
         * This means the database will only return the session when
         * the refresh token belongs to the currently authenticated account.
         */
        RefreshSession refreshSession =
                refreshSessionRepository
                        .findByTokenHashAndIdentityAccountId(
                                refreshTokenHash,
                                accountId
                        )
                        .orElseThrow(InvalidCredentialsException::new);

        /*
         * If the session is still active, mark it as revoked.
         *
         * We intentionally keep the database record rather than deleting
         * it so that the system retains the revocation state.
         */
        if (refreshSession.getRevokedAt() == null) {
            refreshSession.setRevokedAt(Instant.now());
        }

        /*
         * Because refreshSession is a managed JPA entity inside this
         * @Transactional method, Hibernate's dirty checking will detect
         * the changed revokedAt field and issue the UPDATE when the
         * transaction commits.
         */
    }
}
