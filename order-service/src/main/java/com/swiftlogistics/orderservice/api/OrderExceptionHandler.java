package com.swiftlogistics.orderservice.api;

import com.swiftlogistics.orderservice.api.dto.ErrorResponse;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns failures into tidy JSON instead of a Spring stack trace.
 *
 * Without this, a 403 from the role check comes back with an empty body by
 * default, and the dashboard has nothing to show the user beyond the number.
 */
@RestControllerAdvice
public class OrderExceptionHandler {

    /** Role checks and the delivery-status rules, which throw with a status attached. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.name(), ex.getReason()));
    }

    /** A missing or blank field, caught by @Valid before the service is reached. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_FAILED", details));
    }
}
