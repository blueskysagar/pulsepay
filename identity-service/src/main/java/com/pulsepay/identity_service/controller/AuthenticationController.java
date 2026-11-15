package com.pulsepay.identity_service.controller;

import com.pulsepay.identity_service.dto.request.LoginRequest;
import com.pulsepay.identity_service.dto.request.RefreshTokenRequest;
import com.pulsepay.identity_service.dto.response.LoginResponse;
import com.pulsepay.identity_service.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request){
        LoginResponse response = authenticationService.login(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request){
        LoginResponse response = authenticationService.refresh(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT) // Nothing to return
    public void logout(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest, Authentication authentication){
        /*
         * Spring Security injects the Authentication object that was
         * established earlier by JwtAuthenticationFilter.
         *
         * Our JWT filter placed the authenticated account ID into
         * the Authentication principal after successfully verifying
         * the access token.
         */
        String authenticatedAccountId = authentication.getName();
//        *
//     * Pass both pieces of information to the application service:
//     *
//     * 1. The refresh token supplied by the client.
//                * 2. The account identity established by the verified JWT.
//     *
//     * The service can then ensure that the refresh session being
//     * revoked actually belongs to the authenticated account.
//                */
        authenticationService.logout(refreshTokenRequest, authenticatedAccountId);

    }



}
