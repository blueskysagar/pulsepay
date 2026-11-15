package com.pulsepay.wallet_service.dto.request;

import com.pulsepay.wallet_service.enums.WalletStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWalletStatusRequest {
    /*
     * The client explicitly requests a lifecycle transition.
     *
     * @NotNull prevents a request from reaching the business layer
     * without a target status.
     */
    @NotNull
    private WalletStatus status;

}
