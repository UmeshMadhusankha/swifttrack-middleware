package com.swiftlogistics.rosadapter.ros;

/**
 * Body of POST /api/routes.
 *
 * ROS also accepts a list of stops with coordinates, but the middleware only
 * ever has a street address, and ROS derives the rest.
 */
public record RoutePlanRequest(Long orderId, String deliveryAddress) {
}
