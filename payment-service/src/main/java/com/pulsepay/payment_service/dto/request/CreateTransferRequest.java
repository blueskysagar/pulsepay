package com.pulsepay.payment_service.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(
        UUID sourceWalletId,
        UUID destinationWalletId,
        BigDecimal amount,
        String currency
) {
}
