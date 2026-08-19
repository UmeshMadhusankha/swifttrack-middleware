package com.swiftlogistics.orderservice.domain;

/**
 * How far an order has travelled through the saga, in the shape the admin
 * dashboard needs it.
 *
 * The saga itself lives in the orchestrator's own database, which this service
 * deliberately cannot read. What it can do is remember the step name the
 * orchestrator puts on every status announcement, and combine that with the
 * order's own status to say which of the three legacy systems is done, which
 * one is working right now, and which one broke.
 */
public record SagaProgress(String currentStep, String cms, String wms, String ros) {

    /** Cell values for the CMS / WMS / ROS columns. */
    public static final String DONE = "COMPLETED";
    public static final String ACTIVE = "IN_PROGRESS";
    public static final String BROKEN = "FAILED";
    public static final String WAITING = "PENDING";

    /** The saga's three steps, in order, one per legacy system. */
    private static final String[] STEPS = {"BILLING", "STOCK_RESERVATION", "ROUTE_PLANNING"};

    /**
     * Works out the progress of one order.
     *
     * @param status    the order's own status, driven by the orchestrator's events
     * @param sagaStep  the step name from the most recent status announcement,
     *                  null for orders created before that field existed
     */
    public static SagaProgress of(OrderStatus status, String sagaStep) {
        String step = sagaStep != null ? sagaStep : inferStepFrom(status);

        return switch (status) {
            case PENDING -> new SagaProgress("QUEUED", WAITING, WAITING, WAITING);
            case COMPLETED -> new SagaProgress("COMPLETED", DONE, DONE, DONE);
            case COMPENSATING -> unwinding("COMPENSATING", step);
            case FAILED -> unwinding("FAILED", step);
            default -> running(step);
        };
    }

    /**
     * Everything before the active step is done, the active step is working,
     * the rest have not been asked yet.
     */
    private static SagaProgress running(String step) {
        int active = indexOf(step);

        // ROUTE_PLANNED with no further step: all three replied, the
        // orchestrator just has not written COMPLETED yet.
        if (active < 0) {
            return new SagaProgress("COMPLETED", DONE, DONE, DONE);
        }

        String[] cells = new String[STEPS.length];
        for (int i = 0; i < STEPS.length; i++) {
            cells[i] = i < active ? DONE : i == active ? ACTIVE : WAITING;
        }
        return new SagaProgress(step, cells[0], cells[1], cells[2]);
    }

    /**
     * The step named in the announcement is the one that broke. Steps before it
     * had already succeeded; steps after it were never reached.
     */
    private static SagaProgress unwinding(String currentStep, String step) {
        int failed = indexOf(step);

        if (failed < 0) {
            return new SagaProgress(currentStep, WAITING, WAITING, WAITING);
        }

        String[] cells = new String[STEPS.length];
        for (int i = 0; i < STEPS.length; i++) {
            cells[i] = i < failed ? DONE : i == failed ? BROKEN : WAITING;
        }
        return new SagaProgress(currentStep, cells[0], cells[1], cells[2]);
    }

    /**
     * Fallback for orders whose events carried no step name.
     *
     * A status only ever arrives once its step has finished, so the step now in
     * flight is the one after it.
     */
    private static String inferStepFrom(OrderStatus status) {
        return switch (status) {
            case PROCESSING -> "BILLING";
            case BILLED -> "STOCK_RESERVATION";
            case STOCK_RESERVED -> "ROUTE_PLANNING";
            default -> "";
        };
    }

    private static int indexOf(String step) {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i].equals(step)) {
                return i;
            }
        }
        return -1;
    }
}
