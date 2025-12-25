package com.pokergame.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * Service responsible for generating and validating JWT tokens for stateless authentication.
 * No user accounts are required; the token's subject is the player's name.
 */
@Service
public class JwtService {

    @Value("${jwt.secret:change-me-to-a-long-random-string}")
    private String secretKeyString;

    @Value("${jwt.expirationMillis:86400000}") // default 24h
    private long expirationMillis;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        // Ensure we have a base64-encoded secret key suitable for HMAC
        String base64 = Base64.getEncoder().encodeToString(secretKeyString.getBytes());
        byte[] keyBytes = Decoders.BASE64.decode(base64);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a signed JWT for the given player.
     */
    public String generateToken(String playerName) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(playerName)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Validates the token signature and expiry.
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the player name (subject) from a valid token.
     */
    public String extractPlayerName(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
