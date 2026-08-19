package com.swiftlogistics.gateway.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Limits login attempts per IP address to protect against brute-force attacks.
 *
 * Uses a sliding-window counter: if more than MAX_ATTEMPTS requests arrive from
 * the same IP within WINDOW, the request is rejected with 429.
 *
 * This is an in-memory implementation, suitable for a single-instance prototype.
 * A production deployment would use Redis (Spring Cloud Gateway's built-in
 * RedisRateLimiter) so limits are shared across all gateway replicas.
 */
@Component
public class LoginRateLimitFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    /** Maximum login attempts per window. */
    private static final int MAX_ATTEMPTS = 10;

    /** Sliding window size. */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** IP address → (attempt count, window start time) */
    private final Map<String, long[]> counters = new ConcurrentHashMap<>();

    /** Builds the filter that gets attached to the auth route. */
    public GatewayFilter filter() {
        return (exchange, chain) -> {
            String ip = getClientIp(exchange);

            if (isRateLimited(ip)) {
                log.warn("Rate limit exceeded for IP {} on login endpoint", ip);
                return reject(exchange);
            }

            return chain.filter(exchange);
        };
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var address = exchange.getRequest().getRemoteAddress();
        return address != null ? address.getAddress().getHostAddress() : "unknown";
    }

    private boolean isRateLimited(String ip) {
        long now = Instant.now().toEpochMilli();
        long windowMs = WINDOW.toMillis();

        counters.compute(ip, (key, entry) -> {
            if (entry == null || now - entry[1] > windowMs) {
                // New window.
                return new long[]{1, now};
            }
            entry[0]++;
            return entry;
        });

        long[] entry = counters.get(ip);
        return entry != null && entry[0] > MAX_ATTEMPTS;
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"error\":\"RATE_LIMITED\",\"message\":\"Too many login attempts. Please wait a minute.\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}
