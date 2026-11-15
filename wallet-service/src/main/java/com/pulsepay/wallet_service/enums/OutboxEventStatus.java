package com.pulsepay.wallet_service.enums;

public enum OutboxEventStatus {
    PENDING, //this event exists in database, but kafka hasnt received it.
    PROCESSING,
    PUBLISHED
}
