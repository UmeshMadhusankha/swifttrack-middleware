/**
 * The vocabulary the dashboards share: what a status means, what colour it
 * gets, and how far along the saga an order is.
 *
 * Keeping it in one file means the client's timeline, the driver's cards and
 * the admin's pipeline table can never disagree about what "COMPENSATING"
 * looks like.
 */

// --- the client's timeline -------------------------------------------------

/**
 * The six nodes on the client's order timeline.
 *
 * The backend has more states than a customer needs to see, so each status
 * maps to "how far along is this". The timeline only ever moves forward: two
 * steps finishing between two polls cannot make it jump backwards.
 */
export const TIMELINE = [
  { key: 'RECEIVED', label: 'Order Received', detail: 'Saved and queued for processing' },
  { key: 'BILLED', label: 'CMS Billed', detail: 'Billing record created in the client system' },
  { key: 'RESERVED', label: 'Stock Reserved', detail: 'Warehouse allocated the package' },
  { key: 'ROUTED', label: 'Route Planned', detail: 'Delivery route optimised' },
  { key: 'OUT', label: 'Out for Delivery', detail: 'Handed to a driver' },
  { key: 'DELIVERED', label: 'Delivered', detail: 'Signed for at the address' },
];

/** How many timeline nodes are filled in for a given order. */
export function nodesCompleted(order) {
  const byStatus = {
    PENDING: 1,
    PROCESSING: 1,
    BILLED: 2,
    STOCK_RESERVED: 3,
    ROUTE_PLANNED: 4,
    COMPLETED: 5,
    COMPENSATING: 0,
    FAILED: 0,
  };

  const base = byStatus[order.status] ?? 0;

  // The last node belongs to the driver, not the middleware.
  if (order.status === 'COMPLETED' && order.deliveryStatus === 'DELIVERED') {
    return 6;
  }
  return base;
}

// --- how an order is doing, in one word ------------------------------------

export function isFinished(status) {
  return status === 'COMPLETED' || status === 'FAILED';
}

export function hasFailed(status) {
  return status === 'FAILED';
}

export function isUnwinding(status) {
  return status === 'COMPENSATING';
}

/** In progress means the middleware still has work to do on it. */
export function isInProgress(status) {
  return !isFinished(status);
}

// --- colours ---------------------------------------------------------------

/**
 * Tailwind classes for a status badge.
 *
 * Green for done, amber for moving, red for broken — the same three colours
 * everywhere, so a glance at any screen reads the same way.
 */
const BADGE = {
  COMPLETED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  DELIVERED: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  FAILED: 'bg-rose-50 text-rose-700 ring-rose-600/20',
  DELIVERY_FAILED: 'bg-rose-50 text-rose-700 ring-rose-600/20',
  COMPENSATING: 'bg-orange-50 text-orange-700 ring-orange-600/20',
  PENDING: 'bg-slate-100 text-slate-600 ring-slate-500/20',
  PENDING_DELIVERY: 'bg-sky-50 text-sky-700 ring-sky-600/20',
};

const BADGE_IN_FLIGHT = 'bg-amber-50 text-amber-700 ring-amber-600/20';

export function badgeClasses(status) {
  return BADGE[status] || BADGE_IN_FLIGHT;
}

/** Turns PENDING_DELIVERY into "Pending delivery" for display. */
export function humanise(status) {
  if (!status) return '—';
  const words = status.replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** The saga step, worded for someone who is not reading the source code. */
const STEP_LABEL = {
  QUEUED: 'Queued',
  BILLING: 'Billing (CMS)',
  STOCK_RESERVATION: 'Stock reservation (WMS)',
  ROUTE_PLANNING: 'Route planning (ROS)',
  COMPLETED: 'Completed',
  COMPENSATING: 'Rolling back',
  FAILED: 'Failed',
};

export function stepLabel(step) {
  return STEP_LABEL[step] || humanise(step);
}

/** Why a driver could not deliver. Offered as a dropdown, stored as free text. */
export const FAILURE_REASONS = [
  'Recipient not available',
  'Address not found',
  'Package damaged',
];
