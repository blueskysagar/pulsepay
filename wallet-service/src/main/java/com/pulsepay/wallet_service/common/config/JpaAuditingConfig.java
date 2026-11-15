package com.pulsepay.wallet_service.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration // It contains bean definitions and will tell spring here is set up instructions just read and use what is defined here.
@EnableJpaAuditing // This will enable JPA auditing functionality
public class JpaAuditingConfig {
}
