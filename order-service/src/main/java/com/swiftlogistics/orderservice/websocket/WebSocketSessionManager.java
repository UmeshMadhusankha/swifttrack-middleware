package com.swiftlogistics.orderservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Keeps track of every open WebSocket connection and pushes status updates
 * to the right browser as soon as the RabbitMQ event arrives.
 *
 * Sessions are keyed by orderId. A single browser tab watching one order
 * holds exactly one session. Sessions are removed automatically on close
 * or error, so the map never grows unboundedly.
 */
@Component
public class WebSocketSessionManager extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    /** orderId (as String) → open WebSocket session */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public WebSocketSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String orderId = extractOrderId(session);
        if (orderId != null) {
            sessions.put(orderId, session);
            log.info("WebSocket connected for order {}, sessionId={}", orderId, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String orderId = extractOrderId(session);
        if (orderId != null) {
            sessions.remove(orderId);
            log.info("WebSocket closed for order {}, status={}", orderId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String orderId = extractOrderId(session);
        log.warn("WebSocket transport error for order {}: {}", orderId, exception.getMessage());
        sessions.remove(orderId);
    }

    /**
     * Called by OrderStatusListener whenever the saga announces a status change.
     * Sends the update to the browser watching this order, if one is connected.
     */
    public void pushStatusUpdate(Long orderId, String status, String statusDetail) {
        WebSocketSession session = sessions.get(String.valueOf(orderId));
        if (session == null || !session.isOpen()) {
            return; // No browser watching this order right now — that is fine.
        }

        try {
            Map<String, Object> payload = Map.of(
                "orderId", orderId,
                "status", status,
                "statusDetail", statusDetail != null ? statusDetail : ""
            );
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
            log.debug("Pushed status {} to WebSocket for order {}", status, orderId);
        } catch (IOException ex) {
            log.warn("Failed to push status to WebSocket for order {}: {}", orderId, ex.getMessage());
            sessions.remove(String.valueOf(orderId));
        }
    }

    /** Extracts the orderId from the WebSocket URI path (/ws/orders/{orderId}). */
    private String extractOrderId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : null;
        if (path == null) return null;
        // Path is /ws/orders/42 — take the last segment
        String[] parts = path.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }
}
