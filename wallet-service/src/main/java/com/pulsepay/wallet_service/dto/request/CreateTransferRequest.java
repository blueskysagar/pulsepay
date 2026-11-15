package com.pulsepay.wallet_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(
        /**
         * Wallet from which money will be debited.
         */
        @NotNull
        UUID sourceWalletId,

        /**
         * Wallet that will receive the money.
         */
        @NotNull
        UUID destinationWalletId,

        /**
         * Amount of money to transfer.
         *
         * WHY BigDecimal:
         * Money requires exact decimal representation.
         */
        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount
) {

}
