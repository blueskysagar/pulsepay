package com.pulsepay.wallet_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class CreditWalletRequest {
    /*
     * The amount represents money being added to the wallet.
     *
     * BigDecimal is mandatory for monetary values because double/float
     * use binary floating-point representation and can introduce
     * precision errors.
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
    private BigDecimal amount;
}
