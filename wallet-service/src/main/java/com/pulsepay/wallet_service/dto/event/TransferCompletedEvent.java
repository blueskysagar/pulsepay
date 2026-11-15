package com.pulsepay.wallet_service.dto.event;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCompletedEvent(
        UUID transferId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency
) {
}
