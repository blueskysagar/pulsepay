package com.pulsepay.payment_service.client;

import com.pulsepay.payment_service.dto.response.CreateTransferResponse;

import java.math.BigDecimal;
import java.util.UUID;

//We are creating wallet client because we want someone from payment to talk with wallet service to pass the message to do the transfer. This is synchronous communication between payment and wallettransfer.
public interface WalletClient{
    CreateTransferResponse executeTransfer(
            UUID sourceWalletId,
            UUID destinationWalletId,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String accessToken
    );
    CreateTransferResponse executeInternalTransfer(
            UUID sourceWalletId,
            UUID destinationWalletId,
            BigDecimal amount,
            String currency,
            String idempotencyKey
    );
}