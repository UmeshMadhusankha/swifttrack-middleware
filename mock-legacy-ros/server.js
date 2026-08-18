'use strict';

/**
 * Stand-in for SwiftLogistics' Route Optimization System.
 *
 * A modern-looking REST/JSON service: you POST the drops you need to make and
 * it hands back a sequenced route. The ROS adapter will call this and translate
 * the answer into a saga step result.
 *
 * It can also be told to fail on demand, which is how the compensation path in
 * the SAGA orchestrator gets proven rather than assumed.
 */

const express = require('express');
const { DEPOT, optimize, readStops } = require('./routing');

const PORT = Number(process.env.ROS_PORT) || 3001;

/** In-memory store of planned routes, keyed by routeId. Lost on restart, which is fine. */
const routes = new Map();

/**
 * Whether the next request should be rejected.
 *
 * Starts from the environment so docker-compose can switch it on, and can be
 * flipped at runtime through /control/force-failure so a live demo does not
 * need a restart.
 */
let forceFailure = ['1', 'true', 'yes'].includes(String(process.env.ROS_FORCE_FAILURE).toLowerCase());

const app = express();
app.use(express.json());

/** True when this particular request should fail, by toggle or by query string. */
function shouldFail(req) {
  const requested = String(req.query.forceFailure || '').toLowerCase();
  return forceFailure || requested === 'true' || requested === '1';
}

function nextRouteId(orderId) {
  const suffix = Math.random().toString(16).slice(2, 6).toUpperCase();
  return `ROUTE-${orderId ?? 'NA'}-${suffix}`;
}

app.get('/health', (req, res) => {
  res.json({ status: 'UP', service: 'mock-legacy-ros', forceFailure });
});

/**
 * Plans a route.
 *
 * Accepts either a list of stops with coordinates, or just a deliveryAddress
 * string for the simple one-drop case the middleware actually uses.
 */
app.post('/api/routes', (req, res) => {
  const { orderId } = req.body || {};

  if (shouldFail(req)) {
    console.warn(`ROS: rejecting route request for order ${orderId} (forced failure)`);
    return res.status(503).json({
      status: 'FAILURE',
      orderId: orderId ?? null,
      error: 'Route optimization engine unavailable',
    });
  }

  const stops = readStops(req.body || {});
  if (stops.length === 0) {
    return res.status(400).json({
      status: 'FAILURE',
      error: 'Provide either a non-empty "stops" array or a "deliveryAddress" string',
    });
  }

  const plan = optimize(stops);
  const route = {
    routeId: nextRouteId(orderId),
    orderId: orderId ?? null,
    status: 'OPTIMIZED',
    depot: DEPOT,
    ...plan,
    plannedAt: new Date().toISOString(),
  };

  routes.set(route.routeId, route);
  console.log(`ROS: planned ${route.routeId} for order ${orderId} with ${route.stops.length} stop(s)`);

  return res.status(201).json(route);
});

app.get('/api/routes/:routeId', (req, res) => {
  const route = routes.get(req.params.routeId);
  if (!route) {
    return res.status(404).json({ status: 'FAILURE', error: 'No such route' });
  }
  return res.json(route);
});

/**
 * Cancels a planned route.
 *
 * This is the compensating action: if a later saga step fails, the orchestrator
 * needs a way to undo the route it already asked for.
 */
app.delete('/api/routes/:routeId', (req, res) => {
  const route = routes.get(req.params.routeId);
  if (!route) {
    // Undoing something that is not there is not an error. Compensation may be
    // retried, and the second attempt must not fail.
    console.warn(`ROS: cancel requested for unknown route ${req.params.routeId}, treating as done`);
    return res.json({ status: 'SUCCESS', routeId: req.params.routeId, message: 'Nothing to cancel' });
  }

  route.status = 'CANCELLED';
  route.cancelledAt = new Date().toISOString();
  console.log(`ROS: cancelled ${route.routeId}`);

  return res.json({ status: 'SUCCESS', routeId: route.routeId, message: 'Route cancelled' });
});

/** Turns forced failure on or off without restarting. Demo convenience only. */
app.post('/control/force-failure', (req, res) => {
  const { enabled } = req.body || {};
  if (typeof enabled !== 'boolean') {
    return res.status(400).json({ error: 'Body must be {"enabled": true} or {"enabled": false}' });
  }

  forceFailure = enabled;
  console.warn(`ROS: forceFailure is now ${forceFailure}`);
  return res.json({ forceFailure });
});

app.listen(PORT, () => {
  console.log(`Mock ROS (REST/JSON) listening on http://localhost:${PORT}`);
  console.log(`ROS: POST /api/routes    plan a route`);
  console.log(`ROS: DELETE /api/routes/:routeId   cancel one (compensation)`);
  if (forceFailure) {
    console.warn('ROS: started with forced failure ON, every route request will be rejected');
  }
});
