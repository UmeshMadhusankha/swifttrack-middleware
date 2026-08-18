package com.swiftlogistics.authservice.api.dto;

/**
 * What a successful login returns.
 *
 * tokenType is "Bearer" so the frontend knows to send the token back as
 * `Authorization: Bearer <token>`, which is what the gateway will look for.
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        String username,
        String role) {
}
