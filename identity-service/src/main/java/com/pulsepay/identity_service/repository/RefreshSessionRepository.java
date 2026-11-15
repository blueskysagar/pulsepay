package com.pulsepay.identity_service.repository;

import com.pulsepay.identity_service.entity.RefreshSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    Optional<RefreshSession> findByTokenHash(String tokenHash);
    /*
     * Load the refresh session with a database write lock.
     *
     * Refresh-token rotation is concurrency-sensitive: only one request
     * should be allowed to validate and replace the current token at a time.
     */
    //    * PESSIMISTIC_WRITE tells the database:
    //            *
//            * "Lock this row for this transaction because I may modify it."
//            *
//            * We use an explicit JPQL query because "ForUpdate" is NOT
//     * a Spring Data derived-query keyword.
//            */
    //  Find the RefreshSession whose tokenHash equals the supplied parameter.
    @Lock(LockModeType.PESSIMISTIC_WRITE)

    @Query("""
        SELECT rs
        FROM RefreshSession rs
        WHERE rs.tokenHash =:tokenHash
"""

    )
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /*
     * Find a refresh session only when BOTH the refresh-token hash
     * and its owning account match.
     *
     * This is used during logout so an authenticated account cannot
     * revoke another account's refresh session.
     */
//    * PESSIMISTIC_WRITE tells the database:
//            *
//            * "Lock this row for this transaction because I may modify it."
//            *
//            * We use an explicit JPQL query because "ForUpdate" is NOT
//     * a Spring Data derived-query keyword.
//            */
  //  Find the RefreshSession whose tokenHash equals the supplied parameter.

    Optional<RefreshSession> findByTokenHashAndIdentityAccountId( String tokenHash, UUID identityAccountId);
}
