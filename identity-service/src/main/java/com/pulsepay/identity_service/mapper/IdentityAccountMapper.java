package com.pulsepay.identity_service.mapper;

import com.pulsepay.identity_service.dto.request.RegisterAccountRequest;
import com.pulsepay.identity_service.dto.response.RegisterAccountResponse;
import com.pulsepay.identity_service.entity.IdentityAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
//componentModel says generate me the implementation of this interface and create the beans so that we can inject later
//unmappedTargetPolicy says if you forgot to map target field treat it as an error.
@Mapper(componentModel = "spring",
unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IdentityAccountMapper {
    //Request to Entity
    // password can't map automatically because the entity has passwordHash, not password.
    //
    //But email and phone can technically be mapped because both objects have fields called email and phone.
    //
    //We're choosing not to rely on that automatic mapping because the values must be normalized first.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
//    @Mapping(target = "createdAt", ignore = true)
//    @Mapping(target = "updatedAt", ignore = true)
    IdentityAccount toEntity(RegisterAccountRequest request);
    //Entity to Response
    @Mapping(target = "uuid", source = "id")
    RegisterAccountResponse toResponse(IdentityAccount identityAccount);
}
