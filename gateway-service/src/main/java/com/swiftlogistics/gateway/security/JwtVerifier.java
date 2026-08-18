package com.swiftlogistics.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Checks that a token really was issued by the auth service.
 *
 * The gateway never calls auth-service to do this. Both services hold the same
 * secret, so verifying the signature locally is enough to prove the token is
 * genuine and unaltered. That is what keeps auth-service off the critical path
 * of every single request.
 *
 * Expiry is checked here too: jjwt rejects an expired token during parsing.
 */
@Component
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

    private final SecretKey signingKey;

    public JwtVerifier(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the token's claims, or empty if it is forged, altered or expired.
     *
     * Any failure is deliberately reduced to "no". The caller has one decision
     * to make and does not benefit from knowing which of the ways it was
     * invalid, and neither does whoever sent it.
     */
    public Optional<Claims> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(claims);

        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Rejected token: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
