package com.pulsepay.wallet_service.security.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
//COnfigurationProperties directly copies the value from .yml to java object
@ConfigurationProperties(prefix = "pulsepay.security.internal")
public record InternalServiceProperties(String serviceToken) {
}
