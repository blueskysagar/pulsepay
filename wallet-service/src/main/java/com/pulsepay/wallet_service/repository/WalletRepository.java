package com.pulsepay.wallet_service.repository;

import com.pulsepay.wallet_service.entity.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    boolean existsByAccountIdAndCurrency(UUID accountId, String currency);
    Optional<Wallet> findByIdAndAccountId(UUID walletId, UUID accountId);
    Page<Wallet> findAllByAccountId(UUID accountId, Pageable pageable);

}
