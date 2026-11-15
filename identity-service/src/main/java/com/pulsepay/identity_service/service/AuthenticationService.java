package com.pulsepay.identity_service.service;

import com.pulsepay.identity_service.dto.request.LoginRequest;
import com.pulsepay.identity_service.dto.request.RefreshTokenRequest;
import com.pulsepay.identity_service.dto.response.LoginResponse;

public interface AuthenticationService {
    LoginResponse login(LoginRequest request);
    // Refresh an existing authenticated session and issue a new access token
    // together with a rotated refresh token.
    LoginResponse refresh(RefreshTokenRequest request);
    /*
     * Revoke the refresh session belonging to the currently
     * authenticated account.
     *
     * The account ID comes from the verified access token,
     * not from client-supplied request data.
     */
    void logout(RefreshTokenRequest request, String authenticatedAccountId);
}
