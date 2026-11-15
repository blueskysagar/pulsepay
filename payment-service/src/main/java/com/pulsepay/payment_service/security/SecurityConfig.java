package com.pulsepay.payment_service.security;

import com.pulsepay.payment_service.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    @Bean
    //Hey spring these are the security rules and filters you should use for incoming http requests.
    public SecurityFilterChain securityFilterChain(HttpSecurity http)throws Exception{
        http
                //We are using JWT bearer authentication not browser session authentication
                .csrf(csrf -> csrf.disable())
                //do not create http sessions, do not maintain server side login sessions.
                //Stateless authentication means server doesnt rely on stored login session to know who the requester or caller is.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                //Every payment endpoint requires authentication
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                //Run our JwtFilter just before Spring username and password authentication filter
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();

    }
}
