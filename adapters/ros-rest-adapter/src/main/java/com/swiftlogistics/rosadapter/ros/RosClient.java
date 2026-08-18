package com.swiftlogistics.rosadapter.ros;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Calls the route optimization system over plain REST.
 *
 * The friendliest of the three legacy systems: modern JSON over HTTP, so this
 * adapter is mostly about timeouts and about not losing the route id.
 */
@Component
public class RosClient {

    private static final Logger log = LoggerFactory.getLogger(RosClient.class);

    private final WebClient webClient;
    private final Duration responseTimeout;

    public RosClient(@Value("${ros.base-url}") String baseUrl,
                     @Value("${ros.connect-timeout-ms}") int connectTimeoutMs,
                     @Value("${ros.response-timeout-ms}") long responseTimeoutMs) {

        this.responseTimeout = Duration.ofMillis(responseTimeoutMs);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(this.responseTimeout);

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Asks ROS to plan a route and returns the plan, including its route id.
     *
     * WebClient is asynchronous by nature, but this adapter is driven by a
     * RabbitMQ listener that is already running on its own thread, so blocking
     * here is both safe and far easier to read than a chain of callbacks. The
     * block has its own deadline as a backstop, in case the transport-level
     * timeout somehow does not fire.
     */
    public RoutePlanResponse planRoute(long orderId, String deliveryAddress) {
        log.debug("Requesting a route from ROS for order {}", orderId);

        return webClient.post()
                .uri("/api/routes")
                .bodyValue(new RoutePlanRequest(orderId, deliveryAddress))
                .retrieve()
                .bodyToMono(RoutePlanResponse.class)
                .block(responseTimeout.plusSeconds(1));
    }

    /**
     * Cancels a previously planned route. This is the compensating action.
     *
     * ROS treats cancelling an unknown route as a success, so a repeated
     * compensation cannot fail on the second attempt.
     */
    public void cancelRoute(String routeId) {
        log.debug("Cancelling ROS route {}", routeId);

        webClient.delete()
                .uri("/api/routes/{routeId}", routeId)
                .retrieve()
                .toBodilessEntity()
                .block(responseTimeout.plusSeconds(1));
    }

    /** Turns the various transport failures into one readable sentence. */
    public static String describeFailure(Throwable ex) {
        if (ex instanceof ReadTimeoutException) {
            return "ROS did not respond in time";
        }
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
