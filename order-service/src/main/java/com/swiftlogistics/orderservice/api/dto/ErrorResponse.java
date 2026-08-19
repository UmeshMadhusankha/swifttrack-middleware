package com.swiftlogistics.orderservice.api.dto;

import java.time.Instant;

/** A failure, in the same shape every time so the frontend can rely on it. */
public record ErrorResponse(String error, String message, Instant timestamp) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, Instant.now());
    }
}
