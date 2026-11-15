package com.pulsepay.identity_service.security;

import com.pulsepay.identity_service.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration // Tells springboot this class contains bean definitions and should be part of application context.
// in simple words we are writing security rules for any incoming http requests.
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        http
        // This is a REST API, not a browser session-based application.
        // We will use JWT authentication later
                //for our restAPI, we dont need to enable csrf because restAPI do not store user login sessions into browser cookies instead they use JWT header instead.
                .csrf(csrf -> csrf.disable())
                /*
                 * Tell Spring Security that PulsePay does not use
                 * server-side HTTP sessions to remember authentication.
                 *
                 * Every protected request must establish its identity
                 * using its authentication credential, currently the JWT.
                 */
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
              // Define which HTTP requests are public and which require authentication.
                .authorizeHttpRequests(auth -> auth
                        // A new user must be able to register without already
                        // being authenticated.
                        .requestMatchers( "/api/v1/accounts",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh").permitAll()
                        // Everything else requires authentication.
                        .anyRequest().authenticated()
                )
        // Execute our JWT authentication filter before
        // Spring Security's username/password authentication filter.
                /*
                 * Register our JWT authentication filter in Spring Security's
                 * filter chain.
                 *
                 * The JWT filter runs before UsernamePasswordAuthenticationFilter
                 * so that a valid bearer token can establish the authenticated
                 * identity before protected endpoints are evaluated.
                 */
                //UserNamePasswordAuthentication fiter is bydefault spring security filter if it runs first
                //Then in security context holder couldnt see anything because jwt authentication filter is not executed
                //so throws unauthorized error thats the reason we put jwt authentication filter before.
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
        // In above i used chaining because in above http variable or objects we have to write series of rules on them
    }

}
