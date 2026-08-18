package com.swiftlogistics.authservice.api;

import com.swiftlogistics.authservice.api.dto.ErrorResponse;
import com.swiftlogistics.authservice.service.InvalidCredentialsException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns failures into tidy JSON instead of a Spring stack trace.
 *
 * Keeping this in one place means the controller only ever describes the happy
 * path, and every error the service can produce has exactly one wording.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    /** Bad username or password. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_CREDENTIALS", ex.getMessage()));
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
