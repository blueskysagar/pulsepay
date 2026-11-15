package com.pulsepay.identity_service.controller;

import com.pulsepay.identity_service.dto.request.RegisterAccountRequest;
import com.pulsepay.identity_service.dto.response.RegisterAccountResponse;
import com.pulsepay.identity_service.service.IdentityAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor

public class IdentityAccountController {
    private final IdentityAccountService identityAccountService;
    @PostMapping
    public ResponseEntity<RegisterAccountResponse> register(@Valid @RequestBody RegisterAccountRequest request){
        RegisterAccountResponse response = identityAccountService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

}
