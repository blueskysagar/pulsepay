package com.pulsepay.identity_service.entity;

import com.pulsepay.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshSession extends BaseEntity {
    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    /*
     * The account that owns this authentication session.
     *
     * One account can have multiple refresh sessions because the
     * same user may be logged in from multiple devices.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_session_account") // because we dont want data base generate the reandom stuff if we got error then we would know what did happen specifcally
    )
    private IdentityAccount identityAccount;

    /*
     * SHA-256 hash of the refresh token.
     *
     * We never persist the raw refresh token because the database
     * should not contain a directly usable authentication credential.
     */
    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64
    )
    private String tokenHash;

    /*
     * The exact point in time after which this refresh session
     * can no longer be used to obtain new access tokens.
     */
    @Column(name="expires_at", nullable = false)
    private Instant expiresAt;
    /*
     * Null means the session has not been revoked.
     *
     * A timestamp means the session was explicitly revoked.
     *
     * Keeping the timestamp instead of only a boolean gives us
     * useful security and audit information about when revocation occurred.
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;
    /*
     * Records the last time this refresh session was successfully used
     * to obtain a new access token.
     *
     * NULL means the session has never been refreshed yet.
     */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
