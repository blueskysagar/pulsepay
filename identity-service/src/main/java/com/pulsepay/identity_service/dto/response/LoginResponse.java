package com.pulsepay.identity_service.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String accessToken; // Lives for short period of time
    private String refreshToken;//Once access token is expired we get new access token called refresh token
    private String tokenType;
    private Long accessTokenExpiresIn;
}
