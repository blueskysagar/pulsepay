package com.pulsepay.payment_service.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

//application.yml is holding the address of wallet-service
//config use that address to build walletREstClient java object
//And payment code uses that java object to do all the network connection,serialization,deserialzation and all that work.
@Configuration

public class WalletClientConfig {
    @Bean
    public RestClient walletRestClient(@Value("${pulsepay.wallet-service.base-url}") String baseUrl){
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
