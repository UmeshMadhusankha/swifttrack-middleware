/**
 * Turns the order status into the five stages shown in the tracker.
 *
 * The backend has more states than the customer needs to see, and the tracker
 * only ever moves forward, so each status maps to "how far along is this".
 * A status arriving out of order, or one being skipped because two steps
 * finished between two polls, cannot make the display jump backwards.
 */

export const STAGES = [
  { key: 'RECEIVED', label: 'Order received', detail: 'Saved and queued for processing' },
  { key: 'BILLED', label: 'CMS confirmed', detail: 'Client billing record created' },
  { key: 'RESERVED', label: 'Warehouse allocated', detail: 'Stock reserved in the warehouse' },
  { key: 'ROUTED', label: 'Route optimized', detail: 'Delivery route planned' },
  { key: 'DONE', label: 'Complete', detail: 'Ready for delivery' },
];

/** How many stages are finished for a given backend status. */
const STAGES_COMPLETE = {
  PENDING: 1,
  PROCESSING: 1,
  BILLED: 2,
  STOCK_RESERVED: 3,
  ROUTE_PLANNED: 4,
  COMPLETED: 5,
  COMPENSATING: 0,
  FAILED: 0,
};

export function stagesCompleted(status) {
  return STAGES_COMPLETE[status] ?? 0;
}

export function isFinished(status) {
  return status === 'COMPLETED' || status === 'FAILED';
}

export function isUnwinding(status) {
  return status === 'COMPENSATING';
}

export function hasFailed(status) {
  return status === 'FAILED';
}
