package com.pulsepay.wallet_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

//Look the application.yml value and map the required values to the fields in the class
@ConfigurationProperties(prefix="pulsepay.outbox.recovery")
public record OutboxRecoveryProperties(
    Duration staleAfter
)
{}
