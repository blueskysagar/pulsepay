package com.pulsepay.identity_service;

import com.pulsepay.identity_service.security.token.JwtProperties;
import com.pulsepay.identity_service.security.token.RefreshTokenProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
//Take settings from our file plug it into jwt properties and turn it on so rest of the app can use it.
@EnableConfigurationProperties({JwtProperties.class, RefreshTokenProperties.class})
public class IdentityServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdentityServiceApplication.class, args);
	}

}
