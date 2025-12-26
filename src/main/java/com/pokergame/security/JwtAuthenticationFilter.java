package com.pokergame.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Filter that extracts JWT from the Authorisation header and sets up Spring
 * Security context.
 * Runs once per request before hitting the controller.
 */
@SuppressWarnings("NullableProblems")
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtService.isTokenValid(token)) {
                String playerName = jwtService.extractPlayerName(token);

                // Create the pre-authenticated token (authentication already occurred via JWT
                // validation)
                PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
                        playerName, token, Collections.emptyList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set in the security context - now Principal.getName() returns playerName
                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.debug("Authenticated player: {}", playerName);
            }
        }

        filterChain.doFilter(request, response);
    }
}
