package com.swiftlogistics.authservice.api;

import com.swiftlogistics.authservice.api.dto.LoginRequest;
import com.swiftlogistics.authservice.api.dto.LoginResponse;
import com.swiftlogistics.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER = "Bearer";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Exchanges a username and password for a signed token.
     *
     * Wrong credentials come back as 401 from the handler in
     * {@link AuthExceptionHandler}, not as an exception escaping this method.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthenticatedUser authenticated =
                authService.login(request.username(), request.password());

        return ResponseEntity.ok(new LoginResponse(
                authenticated.token(),
                BEARER,
                authenticated.expiresInSeconds(),
                authenticated.username(),
                authenticated.role()));
    }
}
