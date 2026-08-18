package com.swiftlogistics.gateway.config;

import com.swiftlogistics.gateway.security.JwtAuthenticationFilter;
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
 * The routes are written in Java rather than YAML so the JWT filter can be
 * attached to one route and visibly left off the other.
 */
@Configuration
public class RouteConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final String authServiceUrl;
    private final String orderServiceUrl;

    public RouteConfig(JwtAuthenticationFilter jwtFilter,
                       @Value("${gateway.auth-service-url}") String authServiceUrl,
                       @Value("${gateway.order-service-url}") String orderServiceUrl) {
        this.jwtFilter = jwtFilter;
        this.authServiceUrl = authServiceUrl;
        this.orderServiceUrl = orderServiceUrl;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()

                // Open on purpose: this is where a caller goes to GET a token,
                // so it cannot be behind a check for having one.
                .route("auth", route -> route
                        .path("/api/auth/**")
                        .uri(authServiceUrl))

                // Protected. The filter rejects anything without a valid token
                // before the request ever reaches order-service.
                .route("orders", route -> route
                        .path("/api/orders/**")
                        .filters(f -> f.filter(jwtFilter.filter()))
                        .uri(orderServiceUrl))

                .build();
    }
}
