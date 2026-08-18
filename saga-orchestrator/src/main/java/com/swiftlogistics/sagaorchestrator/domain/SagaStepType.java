package com.swiftlogistics.sagaorchestrator.domain;

/**
 * The three steps of the order saga, in the order they must run.
 *
 * Each one knows how to ask its legacy system to do the work, how to ask it to
 * undo the work, and what the client-facing order status becomes on success.
 * The whole workflow is described by this enum, so changing the sequence means
 * editing one file.
 */
public enum SagaStepType {

    /** CMS creates the billing record for the order. */
    BILLING(1, "cms.billing.create", "cms.billing.cancel", "BILLED"),

    /** WMS reserves the package in the warehouse. */
    STOCK_RESERVATION(2, "wms.stock.reserve", "wms.stock.release", "STOCK_RESERVED"),

    /** ROS works out the delivery route. */
    ROUTE_PLANNING(3, "ros.route.plan", "ros.route.cancel", "ROUTE_PLANNED");

    private final int sequence;
    private final String commandRoutingKey;
    private final String compensationRoutingKey;
    private final String successOrderStatus;

    SagaStepType(int sequence, String commandRoutingKey, String compensationRoutingKey,
                 String successOrderStatus) {
        this.sequence = sequence;
        this.commandRoutingKey = commandRoutingKey;
        this.compensationRoutingKey = compensationRoutingKey;
        this.successOrderStatus = successOrderStatus;
    }

    public int sequence() {
        return sequence;
    }

    public String commandRoutingKey() {
        return commandRoutingKey;
    }

    public String compensationRoutingKey() {
        return compensationRoutingKey;
    }

    public String successOrderStatus() {
        return successOrderStatus;
    }
}
