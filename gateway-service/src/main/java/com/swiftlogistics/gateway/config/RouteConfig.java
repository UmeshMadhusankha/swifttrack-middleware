package com.swiftlogistics.gateway.config;

import com.swiftlogistics.gateway.security.JwtAuthenticationFilter;
import com.swiftlogistics.gateway.security.LoginRateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The gateway's map of the system.
 *
 * Everything outside talks to one address, and this decides where each request
 * really goes. The frontend never needs to know that orders live on port 8081
 * and logins on 8086, and neither of those services needs to be exposed.
 *
 * The routes are written in Java rather than YAML so the JWT filter and rate
 * limiter can be attached explicitly to target routes.
 */
@Configuration
public class RouteConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final LoginRateLimitFilter rateLimitFilter;
    private final String authServiceUrl;
    private final String orderServiceUrl;
    private final String sagaOrchestratorUrl;

    public RouteConfig(JwtAuthenticationFilter jwtFilter,
                       LoginRateLimitFilter rateLimitFilter,
                       @Value("${gateway.auth-service-url}") String authServiceUrl,
                       @Value("${gateway.order-service-url}") String orderServiceUrl,
                       @Value("${gateway.saga-orchestrator-url}") String sagaOrchestratorUrl) {
        this.jwtFilter = jwtFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.authServiceUrl = authServiceUrl;
        this.orderServiceUrl = orderServiceUrl;
        this.sagaOrchestratorUrl = sagaOrchestratorUrl;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()

                // Open on purpose: where callers login to GET a token.
                // Rate limited to protect against brute force attacks.
                .route("auth", route -> route
                        .path("/api/auth/**")
                        .filters(f -> f.filter(rateLimitFilter.filter()))
                        .uri(authServiceUrl))

                // Protected. The filter rejects anything without a valid token
                // before the request ever reaches order-service.
                .route("orders", route -> route
                        .path("/api/orders/**")
                        .filters(f -> f.filter(jwtFilter.filter()))
                        .uri(orderServiceUrl))

                // WebSocket connections for live order status updates.
                .route("orders-ws", route -> route
                        .path("/ws/orders/**")
                        .uri(orderServiceUrl.replace("http://", "ws://")))

                // Protected debug/tracking view into the saga orchestrator state.
                .route("saga", route -> route
                        .path("/api/saga/**")
                        .filters(f -> f.filter(jwtFilter.filter()))
                        .uri(sagaOrchestratorUrl))

                .build();
    }
}
