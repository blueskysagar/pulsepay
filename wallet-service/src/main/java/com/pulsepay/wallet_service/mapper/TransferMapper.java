package com.pulsepay.wallet_service.mapper;

import com.pulsepay.wallet_service.dto.request.CreateTransferRequest;
import com.pulsepay.wallet_service.dto.response.CreateTransferResponse;
import com.pulsepay.wallet_service.entity.Transfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps Transfer domain objects to API response DTOs.
 *
 * WHY:
 * The Transfer entity belongs to our domain/persistence layer,
 * while CreateTransferResponse belongs to our API layer.
 *
 * Keeping the mapping here prevents the service from becoming
 * responsible for manually copying entity fields into DTOs.
 */

@Mapper(componentModel = "spring")
public interface TransferMapper {
    /*
     * WHY:
     *
     * Currency comes from the database wallet, not from
     * the client request.
     *
     * The service passes that trusted currency here.
     */
    default Transfer toEntity(CreateTransferRequest request, String currency){
        return new Transfer(
                request.sourceWalletId(),
                request.destinationWalletId(),
                request.amount(),
                currency
        );
    }
    @Mapping(source = "id", target = "transferId")
    CreateTransferResponse toCreateTransferResponse(Transfer transfer);
}
