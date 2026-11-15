package com.pulsepay.wallet_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletPageResponse {
    /*
     * Wallets belonging to the authenticated account
     * for the requested page.
     */
    private List<CreateWalletResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
