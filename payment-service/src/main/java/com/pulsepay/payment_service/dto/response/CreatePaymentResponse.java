package com.pulsepay.payment_service.dto.response;

import com.pulsepay.payment_service.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreatePaymentResponse(
        UUID id,
        UUID sourceWalletId,
        UUID destinationWalletId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
