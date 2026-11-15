package com.pulsepay.identity_service.security.token;

public interface RefreshTokenService {
    String generateRefreshToken();
    String hashRefreshToken(String refreshToken);
}
