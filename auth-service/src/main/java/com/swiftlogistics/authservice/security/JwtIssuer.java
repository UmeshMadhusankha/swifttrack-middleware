package com.swiftlogistics.authservice.security;

import com.swiftlogistics.authservice.domain.AppUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns a verified user into a signed JSON Web Token.
 *
 * A JWT is a tamper-evident ID card. It holds a few plain facts about who the
 * holder is, and a signature made with a secret only the issuer and the
 * verifier know. Anyone can read the contents, but changing so much as one
 * character breaks the signature, so the gateway can trust the card without
 * calling back here to ask about it. That is what keeps the gateway fast and
 * this service off the critical path of every request.
 *
 * Because the contents are readable by anyone holding the token, nothing
 * secret goes inside it. A username and a role are fine; a password is not.
 */
@Component
public class JwtIssuer {

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration expiry;

    public JwtIssuer(@Value("${jwt.secret}") String secret,
                     @Value("${jwt.issuer}") String issuer,
                     @Value("${jwt.expiry-minutes}") long expiryMinutes) {

        // Throws immediately if the secret is too short for HS256, which is far
        // better than starting up and issuing tokens nobody can verify.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expiry = Duration.ofMinutes(expiryMinutes);
    }

    public String issueFor(AppUser user) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("role", user.getRole())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(signingKey)
                .compact();
    }

    /** How long an issued token stays valid, in seconds. Reported to the client. */
    public long expirySeconds() {
        return expiry.toSeconds();
    }
}
