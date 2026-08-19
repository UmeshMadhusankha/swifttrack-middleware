package com.swiftlogistics.orderservice.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the WebSocket endpoint the frontend connects to.
 *
 * URL pattern: ws://localhost:8080/ws/orders/{orderId}
 *
 * Note: this endpoint is on the order-service (port 8081 internally).
 * The gateway forwards WebSocket upgrade requests through to order-service.
 * setAllowedOrigins("*") is acceptable for a prototype; tighten to the
 * frontend origin in production.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketSessionManager sessionManager;

    public WebSocketConfig(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sessionManager, "/ws/orders/*")
                .setAllowedOrigins("*");
    }
}
