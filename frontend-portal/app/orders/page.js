'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import OrderTracker from '../../components/OrderTracker';
import { isFinished } from '../../lib/orderStages';
import {
  clearSession, fetchMyOrders, fetchOrder, getToken, getUser, submitOrder,
} from '../../lib/api';

const POLL_INTERVAL_MS = 2000;

/** Submit an order, then watch it travel through the middleware. */
export default function OrdersPage() {
  const router = useRouter();

  const [user, setUser] = useState(null);
  const [form, setForm] = useState({ recipientName: '', deliveryAddress: '', packageDescription: '' });
  const [tracked, setTracked] = useState(null);
  const [history, setHistory] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // Send anyone without a token back to the login page.
  useEffect(() => {
    if (!getToken()) {
      router.replace('/');
      return;
    }
    setUser(getUser());
  }, [router]);

  const loadHistory = useCallback(async () => {
    try {
      setHistory(await fetchMyOrders());
    } catch {
      // A failed history refresh is not worth interrupting the user over.
    }
  }, []);

  useEffect(() => {
    if (user) loadHistory();
  }, [user, loadHistory]);

  /**
   * Polls the order until it reaches a final state.
   *
   * Polling is deliberately simple: the frontend asks the order service for the
   * current row every couple of seconds. It never touches RabbitMQ. All the
   * event-driven machinery is behind the gateway, and the UI only ever sees the
   * latest status the orchestrator wrote down.
   */
  useEffect(() => {
    if (!tracked || isFinished(tracked.status)) {
      if (tracked && isFinished(tracked.status)) loadHistory();
      return;
    }

    const timer = setInterval(async () => {
      try {
        setTracked(await fetchOrder(tracked.id));
      } catch (err) {
        setError(err.message);
      }
    }, POLL_INTERVAL_MS);

    return () => clearInterval(timer);
  }, [tracked, loadHistory]);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);

    try {
      const created = await submitOrder(form);
      setTracked(created);
      setForm({ recipientName: '', deliveryAddress: '', packageDescription: '' });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  function handleLogout() {
    clearSession();
    router.replace('/');
  }

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  if (!user) return null;

  return (
    <div className="shell">
      <div className="topbar">
        <div className="brand">Swift<span>Logistics</span></div>
        <div>
          <span className="who">{user.username}</span>{' '}
          <button className="ghost" onClick={handleLogout}>Sign out</button>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      <form className="panel" onSubmit={handleSubmit}>
        <h2>New delivery order</h2>

        <label htmlFor="recipientName">Recipient name</label>
        <input id="recipientName" value={form.recipientName} onChange={update('recipientName')} required />

        <label htmlFor="deliveryAddress">Delivery address</label>
        <input id="deliveryAddress" value={form.deliveryAddress} onChange={update('deliveryAddress')} required />

        <label htmlFor="packageDescription">Package description</label>
        <textarea id="packageDescription" value={form.packageDescription} onChange={update('packageDescription')} />

        <button type="submit" disabled={busy}>
          {busy ? 'Submitting…' : 'Submit order'}
        </button>
      </form>

      {tracked && <OrderTracker order={tracked} />}

      {history.length > 0 && (
        <div className="panel">
          <h2>Recent orders</h2>
          <table>
            <thead>
              <tr><th>#</th><th>Recipient</th><th>Status</th><th>Updated</th></tr>
            </thead>
            <tbody>
              {history.slice(0, 8).map((order) => (
                <tr key={order.id}>
                  <td>{order.id}</td>
                  <td>{order.recipientName}</td>
                  <td><StatusText status={order.status} /></td>
                  <td className="muted">{new Date(order.updatedAt).toLocaleTimeString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function StatusText({ status }) {
  const tone =
    status === 'COMPLETED' ? 'ok' :
    status === 'FAILED' ? 'bad' :
    status === 'COMPENSATING' ? 'warn' : 'running';

  return <span className={`badge ${tone}`}>{status}</span>;
}
