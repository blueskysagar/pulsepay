package com.pulsepay.payment_service;
import org.springframework.boot.SpringApplication;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan //go search my project which have classes @ConfigurationProperties then automatically register them and fill them
//with the values from application.yml
@EnableScheduling //Doing this spring will look for ScheduledMethods class
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                PaymentServiceApplication.class,
                args
        );
    }
}