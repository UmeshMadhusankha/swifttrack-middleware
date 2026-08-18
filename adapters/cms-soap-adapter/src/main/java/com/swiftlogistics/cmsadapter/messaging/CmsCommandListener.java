package com.swiftlogistics.cmsadapter.messaging;

import com.swiftlogistics.cmsadapter.cms.CmsClient;
import com.swiftlogistics.cmsadapter.generated.CancelBillingResponse;
import com.swiftlogistics.cmsadapter.generated.SubmitOrderResponse;
import com.swiftlogistics.cmsadapter.messaging.command.StepCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The bridge between RabbitMQ and the SOAP client management system.
 *
 * The orchestrator sends and receives ordinary JSON messages and never learns
 * that XML envelopes exist. That translation is this adapter's only job.
 */
@Component
public class CmsCommandListener {

    private static final Logger log = LoggerFactory.getLogger(CmsCommandListener.class);

    /** The value CMS puts in <status> when it accepted the request. */
    private static final String STATUS_SUCCESS = "SUCCESS";

    private final CmsClient cmsClient;
    private final StepResultPublisher publisher;

    public CmsCommandListener(CmsClient cmsClient, StepResultPublisher publisher) {
        this.cmsClient = cmsClient;
        this.publisher = publisher;
    }

    /**
     * Bills the order.
     *
     * On success the invoice number goes back with the reply so the orchestrator
     * can store it. Without that, a later cancellation would have nothing to
     * quote.
     */
    @RabbitListener(queues = MessagingConstants.BILLING_QUEUE)
    public void onCreateBilling(StepCommand command) {
        log.info("Order {}: submitting to CMS for billing", command.orderId());

        try {
            SubmitOrderResponse response = cmsClient.submitOrder(
                    command.orderId(),
                    command.clientId(),
                    command.recipientName(),
                    command.deliveryAddress(),
                    command.packageDescription());

            if (STATUS_SUCCESS.equals(response.getStatus())) {
                publisher.publishSuccess(command, CommandKind.FORWARD,
                        response.getMessage(), response.getInvoiceNumber());
            } else {
                publisher.publishFailure(command, CommandKind.FORWARD,
                        "CMS rejected the order: " + response.getMessage());
            }

        } catch (Exception ex) {
            // A refused connection, a timeout and a malformed envelope all mean
            // the same thing to the saga: this step did not happen.
            publisher.publishFailure(command, CommandKind.FORWARD,
                    "Could not bill the order in CMS: " + describe(ex));
        }
    }

    /** Voids the invoice raised earlier. This is the compensating action. */
    @RabbitListener(queues = MessagingConstants.CANCEL_QUEUE)
    public void onCancelBilling(StepCommand command) {
        log.warn("Order {}: cancelling CMS billing (compensation), invoice {}",
                command.orderId(), command.externalReference());

        try {
            CancelBillingResponse response =
                    cmsClient.cancelBilling(command.orderId(), command.externalReference());

            if (STATUS_SUCCESS.equals(response.getStatus())) {
                publisher.publishSuccess(command, CommandKind.COMPENSATION, response.getMessage(), null);
            } else {
                // The saga records this as a compensation that could not be
                // completed, which is a state a human has to resolve.
                publisher.publishFailure(command, CommandKind.COMPENSATION,
                        "CMS refused to cancel the invoice: " + response.getMessage());
            }

        } catch (Exception ex) {
            publisher.publishFailure(command, CommandKind.COMPENSATION,
                    "Could not cancel the invoice in CMS: " + describe(ex));
        }
    }

    /** Exception messages are sometimes null, which makes for a useless event. */
    private String describe(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
