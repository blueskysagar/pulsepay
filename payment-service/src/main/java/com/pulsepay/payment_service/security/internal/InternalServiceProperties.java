package com.pulsepay.payment_service.security.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulsepay.security.internal")
public record InternalServiceProperties(String serviceToken) {

}
