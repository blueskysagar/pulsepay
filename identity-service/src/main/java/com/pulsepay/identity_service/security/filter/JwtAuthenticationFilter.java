package com.pulsepay.identity_service.security.filter;

import com.pulsepay.identity_service.security.token.AccessTokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
//We need JWT AUthentication filter because it acts as security guard and inspect the incoming request before sending to the controller

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
        // Read the HTTP Authorization header.
        // Clients send the access token using:
        // Authorization: Bearer <JWT>
        String authorizationHeader = request.getHeader("Authorization");
        // The Authorization header is optional at this stage.
        // Public endpoints such as login and refresh may legitimately
        // receive requests without an access token.
        if(authorizationHeader == null || authorizationHeader.isBlank()){
            filterChain.doFilter(request, response);
            return;
        }
        // The Authorization header must use the Bearer authentication
        // scheme because PulsePay access tokens are JWT bearer tokens.
        if(!authorizationHeader.startsWith("Bearer ")){
            // This filter only handles Bearer authentication.
            // Other authentication schemes are left for other security
            // mechanisms or for Spring Security's authorization rules.
            filterChain.doFilter(request,response);
            return;
        }
        // Remove the "Bearer " authentication scheme prefix
// so that only the compact JWT is passed to the verifier.
        String token = authorizationHeader.substring(7);//Start at character index 7 and take everything after it.
        // Reject an empty Bearer credential rather than passing
// an unusable token to the JWT verification layer.
        if(token.isBlank()){
            filterChain.doFilter(request, response);
            return;
        }
        // Read the authentication currently associated with this request.
        Authentication currentAuthentication = SecurityContextHolder.getContext().getAuthentication();
        // Check existing authentication
        // Read the authentication currently associated with this request.
        // Delegate cryptographic and claim validation to the dedicated
// access-token verifier. The filter should not contain JWT
// parsing or cryptographic implementation details.
        if(currentAuthentication !=null && currentAuthentication.isAuthenticated()){
            // An authenticated identity already exists, so this filter
            // should not replace it unnecessarily.
            filterChain.doFilter(request, response);
            return;
        }
        String accountId;
        try{
            // Delegate JWT validation to the dedicated verifier.
            // Only a token that passes signature and claim validation
            // is allowed to establish an authenticated identity.
            accountId = accessTokenVerifier.verifyAndGetSubject(token);

        }catch (IllegalStateException ex){
            // The supplied JWT could not be trusted.
            // Do not expose cryptographic or parsing details to the client.
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized"
            );
            return;
        }

        // Create Spring Security's representation of the authenticated caller.
// The account ID came from the JWT only after its signature and
// security claims were successfully verified.
//        The account ID came from the JWT subject only after the JWT was
//                * successfully verified by AccessTokenVerifier.
//
//                * Credentials are null because the JWT itself was the authentication
//                * credential and we do not need to retain it here.
// *
// * No authorities are currently assigned because PulsePay has not yet
// * introduced role/permission claims into the access token.
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        accountId,
                        null,
                        Collections.emptyList()
                );
        // Store the authenticated identity in Spring Security's context
// so downstream filters, controllers, and services can access
// the identity associated with this request.

        SecurityContextHolder.getContext().setAuthentication(authentication);
        // Continue processing the request with the authenticated context.
        filterChain.doFilter(request,response);

    }
}
