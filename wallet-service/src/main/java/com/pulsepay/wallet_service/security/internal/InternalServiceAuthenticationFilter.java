package com.pulsepay.wallet_service.security.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String PAYMENT_SERVICE = "paymentservice";
    private final InternalServiceProperties internalServiceProperties;
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        //If Payment service wants to prove thit is payment service it put its credential in HttpHeader.
        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        if(token!=null && token.equals(internalServiceProperties.serviceToken())){
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    PAYMENT_SERVICE,
                    null,
                    AuthorityUtils.createAuthorityList(
                            "ROLE_INTERNAL_SERVICE"
                    )
            );
            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

        }
        filterChain.doFilter(request,response);
    }

}
