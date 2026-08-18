package com.swiftlogistics.cmsadapter.cms;

import com.swiftlogistics.cmsadapter.generated.CancelBillingRequest;
import com.swiftlogistics.cmsadapter.generated.CancelBillingResponse;
import com.swiftlogistics.cmsadapter.generated.SubmitOrderRequest;
import com.swiftlogistics.cmsadapter.generated.SubmitOrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.core.SoapActionCallback;

/**
 * Calls the legacy client management system over SOAP.
 *
 * Not one line of XML appears here. The request and response classes were
 * generated from cms.wsdl at build time, and the template turns them into
 * envelopes on the way out and back again on the way in.
 */
@Component
public class CmsClient {

    private static final Logger log = LoggerFactory.getLogger(CmsClient.class);

    /** Named in the WSDL; some SOAP servers route on this header alone. */
    private static final String SUBMIT_ORDER_ACTION = "http://swiftlogistics.com/cms/SubmitOrder";
    private static final String CANCEL_BILLING_ACTION = "http://swiftlogistics.com/cms/CancelBilling";

    private final WebServiceTemplate webServiceTemplate;

    public CmsClient(WebServiceTemplate cmsWebServiceTemplate) {
        this.webServiceTemplate = cmsWebServiceTemplate;
    }

    /** Registers the order and raises an invoice for it. */
    public SubmitOrderResponse submitOrder(long orderId, String clientId, String recipientName,
                                           String deliveryAddress, String packageDescription) {
        SubmitOrderRequest request = new SubmitOrderRequest();
        request.setOrderId(orderId);
        request.setClientId(clientId);
        request.setRecipientName(recipientName);
        request.setDeliveryAddress(deliveryAddress);
        request.setPackageDescription(packageDescription);

        log.debug("Calling CMS SubmitOrder for order {}", orderId);
        return (SubmitOrderResponse) webServiceTemplate.marshalSendAndReceive(
                request, new SoapActionCallback(SUBMIT_ORDER_ACTION));
    }

    /**
     * Voids the invoice again. This is the compensating action.
     *
     * The invoice number is whatever SubmitOrder handed back. CMS can also find
     * the invoice from the order id alone, so a missing number is not fatal.
     */
    public CancelBillingResponse cancelBilling(long orderId, String invoiceNumber) {
        CancelBillingRequest request = new CancelBillingRequest();
        request.setOrderId(orderId);
        request.setInvoiceNumber(invoiceNumber);

        log.debug("Calling CMS CancelBilling for order {} (invoice {})", orderId, invoiceNumber);
        return (CancelBillingResponse) webServiceTemplate.marshalSendAndReceive(
                request, new SoapActionCallback(CANCEL_BILLING_ACTION));
    }
}
