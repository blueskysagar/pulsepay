package com.pulsepay.wallet_service.service;

import com.pulsepay.wallet_service.dto.request.CreateTransferRequest;
import com.pulsepay.wallet_service.dto.response.CreateTransferResponse;

import java.util.UUID;

/**
 * Defines the business operations supported by the Transfer domain.
 *
 * WHY:
 * The controller should not contain transfer business logic.
 * It should only deal with HTTP.
 *
 * The service represents the application's use case:
 * "Create a wallet-to-wallet transfer."
 */
public interface TransferService {
    /**
     * Executes a wallet-to-wallet transfer for the authenticated account.
     *
     *
     * @return result of the completed transfer
     */
    CreateTransferResponse createTransfer(
            CreateTransferRequest request,
            UUID accountId,
            String idempotencyKey
    );
    CreateTransferResponse createInternalTransfer(
            CreateTransferRequest request,
            String idempotencyKey
    );


}
