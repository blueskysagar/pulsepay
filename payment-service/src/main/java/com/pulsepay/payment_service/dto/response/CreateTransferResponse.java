package com.pulsepay.payment_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferResponse(
        UUID transferId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        BigDecimal amount,
        String currency,
        String status
) {
}
