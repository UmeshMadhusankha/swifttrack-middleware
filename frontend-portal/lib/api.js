'use client';

/**
 * Every call the portal makes to the middleware.
 *
 * Nothing here knows about CMS, WMS, ROS or RabbitMQ. The portal talks to one
 * address, the API gateway, and the gateway works out where each request
 * really belongs. That is the whole benefit of putting a gateway in front.
 */

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

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
 * The gateway reads it, checks the signature, and passes the username through
 * to the order service as a header. The order service never sees the token.
 */
function authorisedHeaders() {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${getToken()}`,
  };
}

export async function submitOrder(order) {
  const response = await fetch(`${API_BASE_URL}/api/orders`, {
    method: 'POST',
    headers: authorisedHeaders(),
    body: JSON.stringify(order),
  });

  if (response.status === 401) {
    throw new Error('Your session has expired. Please log in again.');
  }

  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body.message || 'Could not submit the order');
  }
  return body;
}

/** Called on a timer by the status view. */
export async function fetchOrder(orderId) {
  const response = await fetch(`${API_BASE_URL}/api/orders/${orderId}`, {
    headers: authorisedHeaders(),
  });

  if (!response.ok) {
    throw new Error('Could not read the order status');
  }
  return response.json();
}

export async function fetchMyOrders() {
  const response = await fetch(`${API_BASE_URL}/api/orders`, {
    headers: authorisedHeaders(),
  });

  if (!response.ok) {
    throw new Error('Could not load your orders');
  }
  return response.json();
}
