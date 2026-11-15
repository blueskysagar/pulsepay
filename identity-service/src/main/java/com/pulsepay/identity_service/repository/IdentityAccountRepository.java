package com.pulsepay.identity_service.repository;

import com.pulsepay.identity_service.entity.IdentityAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdentityAccountRepository extends JpaRepository<IdentityAccount, UUID> {
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    // Used during authentication to retrieve the account using
    // the canonical email value produced by EmailNormalizer.
    Optional<IdentityAccount> findByEmail(String email);
}
