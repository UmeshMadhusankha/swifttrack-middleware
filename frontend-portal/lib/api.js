'use client';

/**
 * Every call the portal makes to the middleware.
 *
 * Nothing here knows about CMS, WMS, ROS or RabbitMQ. The portal talks to one
 * address, the API gateway, and the gateway works out where each request
 * really belongs. That is the whole benefit of putting a gateway in front.
 */

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

const TOKEN_KEY = 'swifttrack.token';
const USER_KEY = 'swifttrack.user';

// --- the token -------------------------------------------------------------

export function saveSession(token, username, role) {
  sessionStorage.setItem(TOKEN_KEY, token);
  sessionStorage.setItem(USER_KEY, JSON.stringify({ username, role }));
}

export function getToken() {
  return typeof window === 'undefined' ? null : sessionStorage.getItem(TOKEN_KEY);
}

export function getUser() {
  if (typeof window === 'undefined') return null;
  const raw = sessionStorage.getItem(USER_KEY);
  return raw ? JSON.parse(raw) : null;
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}

/**
 * Reads the role claim straight out of the JWT.
 *
 * A JWT's middle segment is base64url-encoded JSON that anyone can read, so no
 * request is needed to find out what role the token carries. This is only ever
 * used to decide which dashboard to show: the browser cannot verify the
 * signature, so a tampered token would simply be rejected by the gateway on
 * the first real request. Deciding what a user may *do* stays on the server.
 */
export function roleFromToken(token) {
  try {
    const payload = token.split('.')[1];
    const normalised = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalised.padEnd(normalised.length + ((4 - (normalised.length % 4)) % 4), '=');
    return JSON.parse(atob(padded)).role || null;
  } catch {
    return null;
  }
}

/** Where each role lands after signing in. */
export const HOME_FOR_ROLE = {
  CLIENT: '/dashboard/client',
  DRIVER: '/dashboard/driver',
  ADMIN: '/dashboard/admin',
};

export function homeForRole(role) {
  return HOME_FOR_ROLE[role] || '/dashboard/client';
}

// --- calls -----------------------------------------------------------------

/** Exchanges credentials for a token. This is the one call that needs no token. */
export async function login(username, password) {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });

  const body = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(body.message || 'Login failed');
  }
  return body;
}

/**
 * Attaches the token to a request.
 *
 * The gateway reads it, checks the signature, and passes the username and role
 * through to the order service as headers. The order service never sees the
 * token, and the role it acts on is the one the gateway stamped on, not one
 * the browser asked for.
 */
function authorisedHeaders() {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${getToken()}`,
  };
}

/** One place where an HTTP failure becomes a message worth showing someone. */
async function readOrThrow(response, fallback) {
  if (response.status === 401) {
    throw new Error('Your session has expired. Please sign in again.');
  }
  if (response.status === 403) {
    throw new Error('Your account does not have access to this.');
  }

  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body.message || fallback);
  }
  return body;
}

export async function submitOrder(order) {
  const response = await fetch(`${API_BASE_URL}/api/orders`, {
    method: 'POST',
    headers: authorisedHeaders(),
    body: JSON.stringify(order),
  });

  return readOrThrow(response, 'Could not submit the order');
}

export async function fetchOrder(orderId) {
  const response = await fetch(`${API_BASE_URL}/api/orders/${orderId}`, {
    headers: authorisedHeaders(),
  });

  return readOrThrow(response, 'Could not read the order status');
}

/** The signed-in client's own orders. */
export async function fetchMyOrders() {
  const response = await fetch(`${API_BASE_URL}/api/orders`, {
    headers: authorisedHeaders(),
  });

  return readOrThrow(response, 'Could not load your orders');
}

/** Every order in the system, with saga step and per-system state. Admin only. */
export async function fetchAllOrders() {
  const response = await fetch(`${API_BASE_URL}/api/orders/all`, {
    headers: authorisedHeaders(),
  });

  return readOrThrow(response, 'Could not load the order pipeline');
}

/** Orders the middleware has finished with, ready to be driven out. Driver only. */
export async function fetchDeliveries() {
  const response = await fetch(`${API_BASE_URL}/api/orders/deliveries`, {
    headers: authorisedHeaders(),
  });

  return readOrThrow(response, 'Could not load your deliveries');
}

/** Records what happened at the door. Driver only. */
export async function updateDeliveryStatus(orderId, status, reason) {
  const response = await fetch(`${API_BASE_URL}/api/orders/${orderId}/delivery-status`, {
    method: 'PATCH',
    headers: authorisedHeaders(),
    body: JSON.stringify(reason ? { status, reason } : { status }),
  });

  return readOrThrow(response, 'Could not update the delivery');
}
