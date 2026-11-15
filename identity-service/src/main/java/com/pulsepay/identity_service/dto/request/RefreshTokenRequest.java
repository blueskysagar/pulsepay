package com.pulsepay.identity_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter


public class RefreshTokenRequest {
    /*
     * The raw refresh token supplied by the client.
     *
     * This value exists only for the duration of the refresh operation.
     * It must never be persisted; the database stores only its SHA-256 hash.
     */
    @NotBlank
    private String refreshToken;

}
