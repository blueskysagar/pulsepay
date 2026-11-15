package com.pulsepay.wallet_service.security.filter;
import com.pulsepay.wallet_service.security.token.AccessTokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AccessTokenVerifier accessTokenVerifier;
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    )throws ServletException, IOException{
        String authorizationHeader = request.getHeader("Authorization");
        if(authorizationHeader==null || authorizationHeader.isBlank()){
            filterChain.doFilter(request,response);
            return;

        }

        /*
         * STEP 2 — CHECK THE BEARER SCHEME
         *
         * We expect:
         *
         * Authorization: Bearer <token>
         *
         * "Bearer" tells us that the credential is
         * a bearer access token.
         */
        if (!authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }
        /*
         * STEP 3 — EXTRACT THE JWT
         *
         * "Bearer " contains 7 characters:
         *
         * B e a r e r [space]
         * 0 1 2 3 4 5 6
         *
         * Therefore substring(7) removes "Bearer "
         * and leaves only the JWT.
         */
        String token =
                authorizationHeader.substring(7);

        /*
         * A header could technically contain:
         *
         * Authorization: Bearer
         *
         * without an actual token.
         *
         * Treat that as unauthenticated rather than
         * passing an empty token to the verifier.
         */
        if (token.isBlank()) {

            filterChain.doFilter(request, response);
            return;
        }
        /*
         * STEP 4 — DON'T REPLACE AN EXISTING AUTHENTICATION
         *
         * SecurityContext may already contain an
         * authenticated principal.
         *
         * We don't need to authenticate the same request
         * again and overwrite it.
         */
        if(SecurityContextHolder.getContext().getAuthentication() !=null){
            filterChain.doFilter(request,response);
        }
        try{
            String accountId = accessTokenVerifier.verifyAndGetSubject(token);

//        *
//             * STEP 6 — CREATE SPRING SECURITY AUTHENTICATION
//             *
//             * Spring Security needs an Authentication object
//             * representing the authenticated user.
//             *
//             * principal = accountId
//                * credentials = null
//                * authorities = empty
//                *
//                * The account ID becomes the identity that
//             * downstream application code can access.
//                */
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(accountId, null, Collections.emptyList());
            /*
             * STEP 7 — STORE AUTHENTICATION
             *
             * SecurityContextHolder is Spring Security's
             * request-level storage for the authenticated identity.
             *
             * From this point onward, Spring Security can answer:
             *
             * "Who is making this request?"
             *
             * Answer:
             *
             * accountId
             */
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request,response);

    }catch(IllegalStateException ex){
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized");
        }
    }


}
