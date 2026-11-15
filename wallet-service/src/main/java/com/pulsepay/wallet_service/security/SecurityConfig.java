package com.pulsepay.wallet_service.security;

import com.pulsepay.wallet_service.security.filter.JwtAuthenticationFilter;
import com.pulsepay.wallet_service.security.internal.InternalServiceAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
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
    private final InternalServiceAuthenticationFilter internalServiceAuthenticationFilter;
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        /*
         * STEP 1 — DISABLE CSRF
         *
         * PulsePay is a stateless REST API.
         *
         * We authenticate API requests using Bearer access tokens,
         * not browser session cookies.
         *
         * Therefore the traditional browser CSRF protection
         * mechanism is not needed for this API design.
         */
        http.csrf(csrf -> csrf.disable());
        /*
         * STEP 2 — MAKE THE APPLICATION STATELESS
         *
         * We do NOT want Spring Security to maintain
         * an HTTP login session for the user.
         *
         * Every request must authenticate itself using
         * its access token.
         *
         * Request 1 → JWT
         * Request 2 → JWT
         * Request 3 → JWT
         *
         * There is no server-side login session.
         */
        http.sessionManagement(session ->
                session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                )
        );
        /*
         * STEP 3 — DEFINE AUTHORIZATION RULES
         *
         * Wallet Service endpoints require an
         * authenticated account.
         *
         * The JWT filter establishes WHO the caller is.
         *
         * Spring Security then checks:
         *
         * "Is this request authenticated?"
         */
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to("health")).permitAll()
                .anyRequest().authenticated()
        );
        /*
         * STEP 4 — INSERT OUR JWT FILTER
         *
         * We place our filter before Spring Security's
         * UsernamePasswordAuthenticationFilter.
         *
         * WHY?
         *
         * We want our JWT filter to authenticate the request
         * BEFORE Spring Security performs authorization decisions.
         *
         * Flow:
         *
         * JWT Filter
         *      ↓
         * SecurityContext
         *      ↓
         * Authorization
         */
        http.addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
        );
        http.addFilterBefore(
                internalServiceAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
        );
        /*
         * STEP 5 — BUILD THE SECURITY FILTER CHAIN
         *
         * Spring now creates the complete security pipeline
         * using all the configuration above.
         */
        return http.build();

    }
}
