package com.pulsepay.wallet_service.mapper;

import com.pulsepay.wallet_service.dto.request.CreateWalletRequest;
import com.pulsepay.wallet_service.dto.response.CreateWalletResponse;
import com.pulsepay.wallet_service.entity.Wallet;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")

public interface WalletMapper {
    /*
     * Request DTO -> Wallet entity
     *
     * We explicitly define which request fields are mapped
     * into which entity fields.
     */
//    @Mapping(source = "currency", target = "currency")

    // These fields are controlled by the server/business logic,
    // not by the client request.
//    @Mapping(target = "id", ignore = true)
//    @Mapping(target = "accountId", ignore = true)
//    @Mapping(target = "balance", ignore = true)
//    @Mapping(target = "status", ignore = true)
//    @Mapping(target = "createdAt", ignore = true)
//    @Mapping(target = "updatedAt", ignore = true)
//    @Mapping(target = "version", ignore = true)
    Wallet toEntity(CreateWalletRequest request);
    /*
     * Wallet entity -> CreateWalletResponse
     *
     * Explicit source -> target mapping makes the API contract
     * easy to understand and maintain.
     */
//    @Mapping(source = "id", target = "id")
//    @Mapping(source = "accountId", target = "accountId")
//    @Mapping(source = "currency", target = "currency")
//    @Mapping(source = "balance", target = "balance")
//    @Mapping(source = "status", target = "status")
//    @Mapping(source = "createdAt", target = "createdAt")
    CreateWalletResponse toCreateWalletResponse(Wallet wallet);
}
