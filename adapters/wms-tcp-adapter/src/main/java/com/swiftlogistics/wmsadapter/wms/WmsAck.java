package com.swiftlogistics.wmsadapter.wms;

/** The two replies the warehouse daemon can send. */
public enum WmsAck {

    SUCCESS("WMS_ACK_SUCCESS"),
    FAILURE("WMS_ACK_FAILURE");

    private final String wireValue;

    WmsAck(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Matches a raw reply string.
     *
     * Anything we do not recognise counts as a failure. A garbled or unexpected
     * answer is not a reason to assume the warehouse did the work.
     */
    public static WmsAck fromWire(String reply) {
        for (WmsAck ack : values()) {
            if (ack.wireValue.equals(reply)) {
                return ack;
            }
        }
        return FAILURE;
    }
}
