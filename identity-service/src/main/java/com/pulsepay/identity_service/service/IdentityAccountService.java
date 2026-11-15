package com.pulsepay.identity_service.service;

import com.pulsepay.identity_service.dto.request.RegisterAccountRequest;
import com.pulsepay.identity_service.dto.response.RegisterAccountResponse;

public interface IdentityAccountService {
    RegisterAccountResponse register(RegisterAccountRequest request);

}
