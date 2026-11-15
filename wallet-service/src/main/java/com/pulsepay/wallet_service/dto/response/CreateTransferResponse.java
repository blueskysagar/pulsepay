package com.pulsepay.wallet_service.dto.response;

import com.pulsepay.wallet_service.enums.TransferStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferResponse(
        UUID transferId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        BigDecimal amount,
        String currency,
        TransferStatus status

) {
}
