package com.swiftlogistics.rosadapter.ros;

/**
 * What ROS sends back after planning a route.
 *
 * The routeId is the important field: it is the only handle we will ever have
 * on this route, and cancelling it later is impossible without it.
 *
 * ROS returns more fields than these. Spring Boot ignores unknown properties by
 * default, so this record can stay limited to what the adapter actually uses.
 */
public record RoutePlanResponse(
        String routeId,
        String status,
        Double totalDistanceKm,
        Integer estimatedDurationMinutes) {
}
