package com.swiftlogistics.gateway.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Lets the browser call this gateway from the frontend.
 *
 * A browser refuses to let a page on one origin read a response from another
 * unless that other origin says it is allowed. The portal runs on port 3000 and
 * the gateway on 8080, which counts as a different origin, so without this
 * every call from the UI fails before the token is even looked at.
 *
 * The browser also sends a preflight OPTIONS request before anything carrying
 * an Authorization header. That preflight arrives without a token, so it has to
 * be answered here, ahead of the JWT filter.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(@Value("${gateway.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        // PATCH is here for the driver's delivery-status update. A method missing
        // from this list fails at the preflight, before the request is even sent.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
