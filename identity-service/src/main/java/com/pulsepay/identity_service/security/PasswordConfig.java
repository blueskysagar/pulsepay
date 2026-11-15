package com.pulsepay.identity_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration //contain bean definition lot, create Bean and if any Bean method calls another bean method then reuse the existing bean instead of calling others
public class PasswordConfig {
    @Bean
    //Password Encoder is interface, passwordencoder is method name
    public PasswordEncoder passwordEncoder(){
        // Argon2PasswordEncoder is the class that knows how to hash password securely.
        //DefaultsForSpringSecurity_v5_8(); It comes with like how much memory to use, how many times to scramble so password are hard to crack
        //You dont have to pick those numbers spring already does that for you.
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    }
}
