package com.swiftlogistics.gateway.security;

import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * The guard on the protected routes.
 *
 * Applied to /api/orders/** and deliberately not to /api/auth/**, because you
 * cannot require a token on the endpoint whose job is to hand you one.
 *
 * On success it copies the caller's identity into headers for the service
 * behind it, so order-service never has to know what a JWT is. That is the
 * point of doing this at the gateway: authentication happens once, at the
 * edge, instead of being reimplemented in every service.
 */
@Component
public class JwtAuthenticationFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    /** Headers the gateway sets itself, derived from the verified token. */
    private static final String CLIENT_ID_HEADER = "X-Client-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final JwtVerifier jwtVerifier;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    /** Builds the filter that gets attached to a route. */
    public GatewayFilter filter() {
        return (exchange, chain) -> {
            Optional<String> token = readBearerToken(exchange.getRequest());

            if (token.isEmpty()) {
                return reject(exchange, "MISSING_TOKEN", "An Authorization: Bearer token is required");
            }

            Optional<Claims> claims = jwtVerifier.verify(token.get());
            if (claims.isEmpty()) {
                return reject(exchange, "INVALID_TOKEN", "The token is invalid or has expired");
            }

            return chain.filter(exchange.mutate()
                    .request(withIdentityHeaders(exchange.getRequest(), claims.get()))
                    .build());
        };
    }

    private Optional<String> readBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    /**
     * Replaces the identity headers with values taken from the verified token.
     *
     * These are set, never merged. If a caller sends its own X-Client-Id, it is
     * overwritten here: otherwise anyone could log in as themselves and then
     * submit orders in someone else's name just by adding a header.
     */
    private ServerHttpRequest withIdentityHeaders(ServerHttpRequest request, Claims claims) {
        String username = claims.getSubject();
        String role = claims.get("role", String.class);

        log.debug("Authenticated '{}' for {} {}", username, request.getMethod(), request.getPath());

        return request.mutate()
                .header(CLIENT_ID_HEADER, username)
                .header(USER_ROLE_HEADER, role == null ? "" : role)
                .build();
    }

    /** Ends the request here with a 401 and a JSON body the frontend can read. */
    private Mono<Void> reject(ServerWebExchange exchange, String error, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"error\":\"%s\",\"message\":\"%s\"}".formatted(error, message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}
