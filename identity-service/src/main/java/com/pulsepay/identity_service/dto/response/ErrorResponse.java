package com.pulsepay.identity_service.dto.response;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String errorCode,
        String message,
        OffsetDateTime timestamp,
        String path
){

}
