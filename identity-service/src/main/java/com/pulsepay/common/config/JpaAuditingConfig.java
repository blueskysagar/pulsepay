package com.pulsepay.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

@Configuration
@EnableJpaAuditing//activate the mechanisms that understands @Created @LastModified
public class JpaAuditingConfig {
//    @Bean
//    public DateTimeProvider offsetDateTimeProvider() {
//        return () -> Optional.of(OffsetDateTime.now());
//    }
}
