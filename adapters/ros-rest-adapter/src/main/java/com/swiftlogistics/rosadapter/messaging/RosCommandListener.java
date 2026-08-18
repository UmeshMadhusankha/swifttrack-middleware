package com.swiftlogistics.rosadapter.messaging;

import com.swiftlogistics.rosadapter.messaging.command.StepCommand;
import com.swiftlogistics.rosadapter.ros.RosClient;
import com.swiftlogistics.rosadapter.ros.RoutePlanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The bridge between RabbitMQ and the route optimization system.
 *
 * The one thing that matters most here is the route id. ROS hands it back when
 * it plans a route, and it is the only way to cancel that route afterwards. It
 * travels back to the orchestrator on the success reply, gets written onto the
 * saga step, and comes back to this adapter later if the order has to be
 * unwound. Drop it anywhere along that path and compensation quietly becomes
 * impossible.
 */
@Component
public class RosCommandListener {

    private static final Logger log = LoggerFactory.getLogger(RosCommandListener.class);

    private final RosClient rosClient;
    private final StepResultPublisher publisher;

    public RosCommandListener(RosClient rosClient, StepResultPublisher publisher) {
        this.rosClient = rosClient;
        this.publisher = publisher;
    }

    @RabbitListener(queues = MessagingConstants.PLAN_QUEUE)
    public void onPlanRoute(StepCommand command) {
        log.info("Order {}: asking ROS to plan a route", command.orderId());

        try {
            RoutePlanResponse route = rosClient.planRoute(command.orderId(), command.deliveryAddress());

            if (route == null || route.routeId() == null) {
                // A 2xx with no route id is worse than an error: the saga would
                // believe the step succeeded and then have nothing to cancel.
                publisher.publishFailure(command, CommandKind.FORWARD,
                        "ROS accepted the request but returned no route id");
                return;
            }

            log.info("Order {}: ROS planned route {} ({} km)",
                    command.orderId(), route.routeId(), route.totalDistanceKm());

            publisher.publishSuccess(command, CommandKind.FORWARD,
                    "Route planned, " + route.totalDistanceKm() + " km", route.routeId());

        } catch (Exception ex) {
            // A refused connection, a timeout and an explicit 503 all mean the
            // same thing to the saga: this step did not happen.
            publisher.publishFailure(command, CommandKind.FORWARD,
                    "Could not plan a route: " + RosClient.describeFailure(ex));
        }
    }

    /** Cancels the route planned earlier. This is the compensating action. */
    @RabbitListener(queues = MessagingConstants.CANCEL_QUEUE)
    public void onCancelRoute(StepCommand command) {
        String routeId = command.externalReference();
        log.warn("Order {}: cancelling ROS route {} (compensation)", command.orderId(), routeId);

        if (routeId == null || routeId.isBlank()) {
            // No id means no route was ever planned, so there is nothing to
            // undo. Reporting success keeps the saga unwinding instead of
            // stalling on work that does not exist.
            log.warn("Order {}: no route id recorded, nothing to cancel", command.orderId());
            publisher.publishSuccess(command, CommandKind.COMPENSATION, "No route to cancel", null);
            return;
        }

        try {
            rosClient.cancelRoute(routeId);
            publisher.publishSuccess(command, CommandKind.COMPENSATION, "Route " + routeId + " cancelled", null);

        } catch (Exception ex) {
            publisher.publishFailure(command, CommandKind.COMPENSATION,
                    "Could not cancel route " + routeId + ": " + RosClient.describeFailure(ex));
        }
    }
}
