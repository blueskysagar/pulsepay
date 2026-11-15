package com.pulsepay.wallet_service.dto.response;

import com.pulsepay.wallet_service.enums.WalletStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWalletResponse {
    private UUID id;
    private UUID accountId;
    private String currency;
    private BigDecimal balance;
    private WalletStatus status;
    private Instant createdAt;

}
