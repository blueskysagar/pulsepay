package com.pulsepay.notification_service.messaging.event;

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
