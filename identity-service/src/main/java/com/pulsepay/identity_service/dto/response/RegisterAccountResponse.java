package com.pulsepay.identity_service.dto.response;

import com.pulsepay.identity_service.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class RegisterAccountResponse {
    private UUID uuid;
    private String name;
    private String email;
    private String phone;
    private AccountStatus status;
    private Instant createdAt;


}
