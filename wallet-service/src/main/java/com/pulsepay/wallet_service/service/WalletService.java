package com.pulsepay.wallet_service.service;

import com.pulsepay.wallet_service.dto.request.CreateWalletRequest;
import com.pulsepay.wallet_service.dto.response.CreateWalletResponse;
import com.pulsepay.wallet_service.dto.response.WalletPageResponse;
import com.pulsepay.wallet_service.enums.WalletStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface WalletService {
    CreateWalletResponse createWallet(
            UUID accountId,
            CreateWalletRequest request
    );
    CreateWalletResponse getWallet(UUID walletId, UUID accountId);
    WalletPageResponse getWallets(UUID accountId, Pageable pageable);
    CreateWalletResponse updateWalletStatus(UUID walletId, UUID accountId, WalletStatus newStatus);
    CreateWalletResponse creditWallet(UUID walletId, UUID accountId, BigDecimal amount, String idempotencyKey);
}
