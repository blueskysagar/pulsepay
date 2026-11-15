package com.pulsepay.wallet_service;

import com.pulsepay.wallet_service.config.OutboxRecoveryProperties;
import org.springframework.boot.SpringApplication;
import com.pulsepay.wallet_service.security.token.JwtProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties({JwtProperties.class,OutboxRecoveryProperties.class})  //Activate it and register as bean.
@EnableScheduling //look for methods using @Scheduled and run them automatically

public class WalletServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}