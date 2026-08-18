package com.swiftlogistics.orderservice.api;

import com.swiftlogistics.orderservice.api.dto.CreateOrderRequest;
import com.swiftlogistics.orderservice.api.dto.OrderResponse;
import com.swiftlogistics.orderservice.domain.Order;
import com.swiftlogistics.orderservice.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * The client id comes from a header for now. Once the API gateway is in
     * place it will read the JWT and set this header itself, so this service
     * never has to know anything about tokens.
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

    /** Polled by the frontend every few seconds to show live status. */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        return orderService.findById(orderId)
                .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<OrderResponse> listOrders(@RequestParam(required = false) String clientId) {
        return orderService.findForClient(clientId).stream()
                .map(OrderResponse::from)
                .toList();
    }
}
