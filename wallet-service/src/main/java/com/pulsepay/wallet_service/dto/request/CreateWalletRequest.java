package com.pulsepay.wallet_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWalletRequest {
    @NotBlank
    @Size(min = 3, max = 3) // Expecting 3 character currency codes
    private String currency;


}
