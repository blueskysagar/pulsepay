package com.pulsepay.wallet_service.service.cache;

import java.util.UUID;

public final class WalletCacheKey {
    private static final String PREFIX = "wallet:v1:";
    private WalletCacheKey(){
        // Do not allow new WalletCacheKey anywhere outside this file.
    }
    public static String forWallet(UUID accountId, UUID walletId){
        return PREFIX + accountId + ":" + walletId;
    }

}
