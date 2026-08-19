package com.swiftlogistics.orderservice.api;

import com.swiftlogistics.orderservice.api.dto.AdminOrderResponse;
import com.swiftlogistics.orderservice.api.dto.CreateOrderRequest;
import com.swiftlogistics.orderservice.api.dto.OrderResponse;
import com.swiftlogistics.orderservice.api.dto.UpdateDeliveryStatusRequest;
import com.swiftlogistics.orderservice.domain.Order;
import com.swiftlogistics.orderservice.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /**
     * Set by the API gateway from the verified JWT, never by the caller.
     *
     * The gateway overwrites whatever arrived in this header, and order-service
     * is not published outside the compose network, so by the time a request
     * gets here the value is trustworthy. That is the only reason a plain
     * string comparison is enough to guard an endpoint.
     */
    private static final String ROLE_HEADER = "X-User-Role";

    private static final String ADMIN = "ADMIN";
    private static final String DRIVER = "DRIVER";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * The client id comes from a header set by the API gateway, which reads it
     * from the JWT, so this service never has to know anything about tokens.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous") String clientId,
            @Valid @RequestBody CreateOrderRequest request) {

        Order order = orderService.placeOrder(
                clientId,
                request.recipientName(),
                request.deliveryAddress(),
                request.packageDescription());

        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    /**
     * Every order in the system with its saga step. Admin only.
     *
     * Declared above the /{orderId} mapping for readability; Spring picks the
     * literal path over the template regardless of order, so "all" is never
     * mistaken for an order id.
     */
    @GetMapping("/all")
    public List<AdminOrderResponse> listAllOrders(
            @RequestHeader(value = ROLE_HEADER, required = false) String role) {

        requireRole(role, ADMIN);

        return orderService.findAll().stream()
                .map(AdminOrderResponse::from)
                .toList();
    }

    /**
     * The driver's work list: orders the middleware has finished processing.
     * Driver only.
     */
    @GetMapping("/deliveries")
    public List<OrderResponse> listDeliveries(
            @RequestHeader(value = ROLE_HEADER, required = false) String role) {

        requireRole(role, DRIVER);

        return orderService.findReadyForDelivery().stream()
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * Records the outcome of a physical delivery. Driver only.
     *
     * This writes the order row directly. It is deliberately not a saga step:
     * the middleware has already finished with the order by the time a driver
     * is at the door, and a failed delivery does not need CMS, WMS or ROS
     * unwound.
     */
    @PatchMapping("/{orderId}/delivery-status")
    public ResponseEntity<OrderResponse> updateDeliveryStatus(
            @RequestHeader(value = ROLE_HEADER, required = false) String role,
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateDeliveryStatusRequest request) {

        requireRole(role, DRIVER);

        try {
            return orderService.recordDeliveryOutcome(orderId, request.status(), request.reason())
                    .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
                    .orElseGet(() -> ResponseEntity.notFound().build());

        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    /** Polled by the frontend every few seconds to show live status. */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        return orderService.findById(orderId)
                .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Lists only the calling client's own orders.
     *
     * The client id comes from the same gateway-set header as on create, never
     * from a query parameter. Letting the caller name the client they wanted
     * would mean anyone could read anyone else's orders just by asking.
     */
    @GetMapping
    public List<OrderResponse> listOrders(
            @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous") String clientId) {

        return orderService.findForClient(clientId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * Stops the request with a 403 unless the token carried the required role.
     *
     * 403 rather than 401 on purpose: the caller proved who they are at the
     * gateway, they simply are not allowed here.
     */
    private void requireRole(String role, String required) {
        if (!required.equalsIgnoreCase(role == null ? "" : role.trim())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This endpoint requires the " + required + " role");
        }
    }
}
