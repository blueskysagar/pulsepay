package com.pulsepay.payment_service.security.filter;

import com.pulsepay.payment_service.security.token.AccessTokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AccessTokenVerifier accessTokenVerifier;
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException{
        //Read the authorization header
        String authorizationHeader = request.getHeader("Authorization");
        //If no header let the spring handle it
        if(authorizationHeader==null || authorizationHeader.isBlank()){
            filterChain.doFilter(request,response);
            return;
        }
        //We understand the bearer tokens
        if(!authorizationHeader.startsWith("Bearer")){
            filterChain.doFilter(request,response);
            return;
        }
        //Remove the bearer and keep only JWT
        String token = authorizationHeader.substring(7);
        if(token.isBlank()){
            filterChain.doFilter(request,response);
            return;
        }
        try{
            //Ask our verifier is our jwt trustworthy
            String accountId = accessTokenVerifier.verifyAndGetSubject(token);
            //Create Spring security authentication
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            accountId,
                            token,
                            Collections.emptyList()
                    );
            //Store the authenticated identity
            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);
            //continue the request
            filterChain.doFilter(request,response);

        }catch (IllegalStateException exception){
            //JWt was invalid
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized"
            );
        }



    }

}
