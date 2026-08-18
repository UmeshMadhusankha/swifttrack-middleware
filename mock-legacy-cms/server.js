'use strict';

/**
 * Stand-in for SwiftLogistics' Client Management System.
 *
 * The oldest-feeling of the three: it speaks SOAP, meaning every request and
 * reply is an XML envelope, and the shape of that XML is fixed by cms.wsdl.
 * Hiding this from the rest of the middleware is the CMS adapter's whole job.
 *
 * Two failure switches are provided so the SAGA orchestrator's compensation
 * logic can be proven on demand:
 *
 *   forceFailure       SubmitOrder replies FAILURE     (the saga fails at step 1)
 *   forceCancelFailure CancelBilling replies FAILURE   (the undo itself fails)
 *
 * Each can be set from the environment, per request via ?forceFailure=true, or
 * flipped live through POST /control/force-failure.
 */

const fs = require('fs');
const path = require('path');
const express = require('express');
const soap = require('soap');

const PORT = Number(process.env.CMS_PORT) || 3002;

/**
 * Where clients should be told this service lives.
 *
 * A WSDL carries its own address in <soap:address location="...">, and a
 * generated client will call whatever it finds there. Inside Docker the host
 * name is cms-mock, not localhost, so the address is rewritten at startup
 * rather than hardcoded in the file.
 */
const PUBLIC_URL = process.env.CMS_PUBLIC_URL || `http://localhost:${PORT}`;

const WSDL_XML = fs
  .readFileSync(path.join(__dirname, 'cms.wsdl'), 'utf8')
  .replace('http://localhost:3002/cms', `${PUBLIC_URL}/cms`);

const isEnabled = (value) => ['1', 'true', 'yes'].includes(String(value).toLowerCase());

let forceFailure = isEnabled(process.env.CMS_FORCE_FAILURE);
let forceCancelFailure = isEnabled(process.env.CMS_FORCE_CANCEL_FAILURE);

/** Invoices raised so far, keyed by order id. In memory only. */
const invoices = new Map();

/** Honours the live toggle as well as a per-request ?forceFailure=true. */
function requestWantsFailure(req, toggle) {
  const requested = String(req && req.query ? req.query.forceFailure : '').toLowerCase();
  return toggle || requested === 'true' || requested === '1';
}

function newInvoiceNumber(orderId) {
  const suffix = Math.random().toString(16).slice(2, 6).toUpperCase();
  return `INV-${orderId}-${suffix}`;
}

/**
 * The operations named in cms.wsdl.
 *
 * The nesting must mirror the WSDL exactly: service name, then port name, then
 * operation names. node-soap uses that path to route an incoming envelope to
 * the right function.
 *
 * The `callback` parameter is unused because returning a plain object is
 * enough; node-soap wraps whatever is returned in the response envelope.
 */
const cmsService = {
  CmsService: {
    CmsPort: {
      SubmitOrder(args, _callback, _headers, req) {
        const orderId = args.orderId;

        if (requestWantsFailure(req, forceFailure)) {
          console.warn(`CMS: rejecting order ${orderId} (forced failure)`);
          return {
            status: 'FAILURE',
            message: 'Client account is on credit hold, billing refused',
          };
        }

        const invoiceNumber = newInvoiceNumber(orderId);
        invoices.set(String(orderId), {
          orderId,
          invoiceNumber,
          clientId: args.clientId,
          recipientName: args.recipientName,
          deliveryAddress: args.deliveryAddress,
          packageDescription: args.packageDescription || null,
          status: 'BILLED',
          raisedAt: new Date().toISOString(),
        });

        console.log(`CMS: billed order ${orderId} as ${invoiceNumber}`);
        return {
          status: 'SUCCESS',
          invoiceNumber,
          message: `Order ${orderId} accepted and billed`,
        };
      },

      CancelBilling(args, _callback, _headers, req) {
        const orderId = args.orderId;

        if (requestWantsFailure(req, forceCancelFailure)) {
          console.error(`CMS: refusing to cancel billing for order ${orderId} (forced failure)`);
          return {
            status: 'FAILURE',
            message: 'Billing period already closed, invoice cannot be voided',
          };
        }

        const invoice = invoices.get(String(orderId));
        if (!invoice) {
          // Cancelling something that was never billed is not an error.
          // Compensation can be retried, and a second attempt must still succeed.
          console.warn(`CMS: no invoice for order ${orderId}, treating cancellation as done`);
          return { status: 'SUCCESS', message: 'No invoice to cancel' };
        }

        invoice.status = 'CANCELLED';
        invoice.cancelledAt = new Date().toISOString();

        console.log(`CMS: cancelled ${invoice.invoiceNumber} for order ${orderId}`);
        return {
          status: 'SUCCESS',
          message: `Invoice ${invoice.invoiceNumber} cancelled`,
        };
      },
    },
  },
};

const app = express();

// Plain REST helpers for driving the demo. express.json() is attached per route
// rather than globally, because the SOAP endpoint needs its body left untouched
// so that node-soap can read the raw XML off the request stream.

app.get('/health', (req, res) => {
  res.json({ status: 'UP', service: 'mock-legacy-cms', forceFailure, forceCancelFailure });
});

/** Everything billed so far, handy for checking that compensation really ran. */
app.get('/control/invoices', (req, res) => {
  res.json([...invoices.values()]);
});

app.post('/control/force-failure', express.json(), (req, res) => {
  const { submitOrder, cancelBilling } = req.body || {};

  if (typeof submitOrder === 'boolean') {
    forceFailure = submitOrder;
  }
  if (typeof cancelBilling === 'boolean') {
    forceCancelFailure = cancelBilling;
  }

  console.warn(`CMS: forceFailure=${forceFailure} forceCancelFailure=${forceCancelFailure}`);
  res.json({ forceFailure, forceCancelFailure });
});

const server = app.listen(PORT, () => {
  console.log(`Mock CMS (SOAP/XML) listening on http://localhost:${PORT}`);
  console.log(`CMS: WSDL at ${PUBLIC_URL}/cms?wsdl`);
  if (forceFailure) {
    console.warn('CMS: started with SubmitOrder failure ON');
  }
  if (forceCancelFailure) {
    console.warn('CMS: started with CancelBilling failure ON');
  }
});

// Attaches the SOAP endpoint at /cms and serves the contract at /cms?wsdl.
soap.listen(app, '/cms', cmsService, WSDL_XML, () => {
  console.log('CMS: SOAP endpoint ready at /cms');
});

module.exports = { app, server };
