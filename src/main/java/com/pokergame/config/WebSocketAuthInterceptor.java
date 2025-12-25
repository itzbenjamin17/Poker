package com.pokergame.config;

import com.pokergame.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Intercepts STOMP messages to authenticate WebSocket connections using JWT.
 * On CONNECT, extracts and validates the JWT token from headers and sets the
 * Principal.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    @Autowired
    private JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Get Authorization header from STOMP connect headers
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                if (jwtService.isTokenValid(token)) {
                    String playerName = jwtService.extractPlayerName(token);

                    // Set the user principal for this WebSocket session
                    accessor.setUser(new Principal() {
                        @Override
                        public String getName() {
                            return playerName;
                        }
                    });

                    logger.debug("WebSocket authenticated for player: {}", playerName);
                } else {
                    // Log invalid token but do not reject the handshake; leave session
                    // unauthenticated
                    logger.warn("Invalid JWT token in WebSocket CONNECT");
                }
            } else {
                // No Authorization header is fine for public handshake; do not reject
                // connection
                logger.debug("No Authorization header in WebSocket CONNECT");
            }
        }

        return message;
    }
}
