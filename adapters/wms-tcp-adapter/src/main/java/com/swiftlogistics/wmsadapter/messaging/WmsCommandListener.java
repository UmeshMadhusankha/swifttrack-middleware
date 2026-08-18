package com.swiftlogistics.wmsadapter.messaging;

import com.swiftlogistics.wmsadapter.messaging.command.StepCommand;
import com.swiftlogistics.wmsadapter.wms.WmsAck;
import com.swiftlogistics.wmsadapter.wms.WmsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The bridge between RabbitMQ and the warehouse socket.
 *
 * This is the whole point of an adapter: on one side it speaks the middleware's
 * language of JSON events, on the other it speaks 24 raw bytes over TCP, and
 * neither side ever learns about the other.
 */
@Component
public class WmsCommandListener {

    private static final Logger log = LoggerFactory.getLogger(WmsCommandListener.class);

    /** The command words the warehouse daemon understands. */
    private static final String RESERVE = "RESERVE";
    private static final String RELEASE = "RELEASE";

    private final WmsClient wmsClient;
    private final StepResultPublisher publisher;

    public WmsCommandListener(WmsClient wmsClient, StepResultPublisher publisher) {
        this.wmsClient = wmsClient;
        this.publisher = publisher;
    }

    @RabbitListener(queues = MessagingConstants.RESERVE_QUEUE)
    public void onReserveStock(StepCommand command) {
        log.info("Order {}: reserving warehouse stock", command.orderId());
        callWarehouse(command, RESERVE, CommandKind.FORWARD);
    }

    @RabbitListener(queues = MessagingConstants.RELEASE_QUEUE)
    public void onReleaseStock(StepCommand command) {
        log.warn("Order {}: releasing warehouse stock (compensation)", command.orderId());
        callWarehouse(command, RELEASE, CommandKind.COMPENSATION);
    }

    /**
     * Runs one command against the warehouse and always answers the orchestrator.
     *
     * Every way this can go wrong ends in the same place: a failure event. A
     * refused connection, a daemon that never answers, and an explicit
     * WMS_ACK_FAILURE are all just "this step did not happen" as far as the saga
     * is concerned.
     *
     * Nothing is allowed to escape this method. An exception thrown out of a
     * listener would discard the message with no reply sent, and the orchestrator
     * would sit waiting for its watchdog to notice. The watchdog is meant to be
     * the last line of defence, not the normal way failures surface.
     */
    private void callWarehouse(StepCommand command, String wmsCommand, CommandKind kind) {
        try {
            WmsAck ack = wmsClient.send(command.orderId(), wmsCommand);

            if (ack == WmsAck.SUCCESS) {
                publisher.publishSuccess(command, kind, "Warehouse acknowledged " + wmsCommand);
            } else {
                publisher.publishFailure(command, kind, "Warehouse rejected " + wmsCommand);
            }

        } catch (Exception ex) {
            publisher.publishFailure(command, kind,
                    "Could not complete " + wmsCommand + " at the warehouse: " + ex.getMessage());
        }
    }
}
