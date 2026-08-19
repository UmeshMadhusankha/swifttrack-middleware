'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Shell, { LiveIndicator } from '../../../components/Shell';
import StatCard, { StatusBadge } from '../../../components/StatCard';
import OrderTimeline from '../../../components/OrderTimeline';
import { CloseIcon, PlusIcon } from '../../../components/Icons';
import { usePolling } from '../../../lib/usePolling';
import { fetchMyOrders, submitOrder } from '../../../lib/api';
import { hasFailed, humanise, isInProgress, stepLabel } from '../../../lib/orderStages';

/**
 * What a SwiftLogistics client sees: their own orders, a form to add one, and
 * a timeline showing where any single order has got to.
 *
 * The list comes from GET /api/orders, which returns only the caller's own
 * orders — the gateway tells the order service who is asking, so there is no
 * client id in the request for anyone to tamper with.
 */
export default function ClientDashboard() {
  const [showForm, setShowForm] = useState(false);
  const [selectedId, setSelectedId] = useState(null);

  const { data, error, updatedAt, refresh } = usePolling(fetchMyOrders, 3000);
  const orders = useMemo(() => data || [], [data]);

  const stats = useMemo(
    () => ({
      total: orders.length,
      inProgress: orders.filter((o) => isInProgress(o.status)).length,
      completed: orders.filter((o) => o.status === 'COMPLETED').length,
      failed: orders.filter((o) => hasFailed(o.status)).length,
    }),
    [orders],
  );

  // Kept as an id rather than the order object, so the open detail view picks
  // up each poll's fresh data instead of freezing on the row that was clicked.
  const selected = orders.find((order) => order.id === selectedId) || null;

  const sections = [
    { href: '#overview', label: 'Overview', icon: <Dot /> },
    { href: '#orders', label: 'My Orders', icon: <Dot /> },
  ];

  return (
    <Shell
      role="CLIENT"
      title="Client Dashboard"
      subtitle="Submit delivery orders and follow them through the middleware"
      sections={sections}
      live={<LiveIndicator updatedAt={updatedAt} error={error} />}
    >
      <section id="overview" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Total orders" value={stats.total} accent="brand" icon={<Dot />} />
        <StatCard label="In progress" value={stats.inProgress} accent="amber" icon={<Dot />} />
        <StatCard label="Completed" value={stats.completed} accent="emerald" icon={<Dot />} />
        <StatCard label="Failed" value={stats.failed} accent="rose" icon={<Dot />} />
      </section>

      <section id="orders" className="mt-8">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">My Orders</h2>
            <p className="text-sm text-slate-500">Select an order to see its progress</p>
          </div>

          <button className="btn-primary" onClick={() => setShowForm(true)}>
            <PlusIcon />
            New Order
          </button>
        </div>

        <OrdersTable orders={orders} selectedId={selectedId} onSelect={setSelectedId} />
      </section>

      {selected && (
        <section className="mt-8 animate-rise">
          <OrderDetail order={selected} onClose={() => setSelectedId(null)} />
        </section>
      )}

      {showForm && (
        <NewOrderModal
          onClose={() => setShowForm(false)}
          onCreated={(order) => {
            setShowForm(false);
            setSelectedId(order.id);
            refresh();
          }}
        />
      )}
    </Shell>
  );
}

function OrdersTable({ orders, selectedId, onSelect }) {
  if (orders.length === 0) {
    return (
      <div className="card p-12 text-center">
        <p className="text-sm font-medium text-slate-900">No orders yet</p>
        <p className="mt-1 text-sm text-slate-500">
          Use the New Order button to submit your first delivery.
        </p>
      </div>
    );
  }

  return (
    <div className="card overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50/80 text-left">
              <Th>Order</Th>
              <Th>Delivery address</Th>
              <Th>Submitted</Th>
              <Th>Status</Th>
              <Th className="text-right">Stage</Th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {orders.map((order) => (
              <tr
                key={order.id}
                onClick={() => onSelect(order.id)}
                className={`cursor-pointer transition ${
                  order.id === selectedId ? 'bg-brand-50/70' : 'hover:bg-slate-50'
                }`}
              >
                <td className="px-5 py-4 font-semibold text-slate-900 tabular">#{order.id}</td>
                <td className="px-5 py-4 text-slate-600">
                  <p className="max-w-xs truncate">{order.deliveryAddress}</p>
                  <p className="mt-0.5 max-w-xs truncate text-xs text-slate-400">
                    {order.packageDescription || 'No description'}
                  </p>
                </td>
                <td className="px-5 py-4 text-slate-500 tabular">
                  {new Date(order.createdAt).toLocaleString([], {
                    day: '2-digit',
                    month: 'short',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </td>
                <td className="px-5 py-4">
                  <StatusBadge status={order.status} label={humanise(order.status)} />
                </td>
                <td className="px-5 py-4 text-right text-xs text-slate-500">
                  {stepLabel(order.sagaStep)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function OrderDetail({ order, onClose }) {
  return (
    <div className="space-y-4">
      <div className="card p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-brand-600">
              Order #{order.id}
            </p>
            <h3 className="mt-1 text-xl font-bold text-slate-900">{order.deliveryAddress}</h3>
            <p className="mt-1 text-sm text-slate-500">
              {order.packageDescription || 'No package description'}
            </p>
          </div>

          <div className="flex items-center gap-3">
            <StatusBadge status={order.status} label={humanise(order.status)} />
            <button
              onClick={onClose}
              aria-label="Close order detail"
              className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
            >
              <CloseIcon />
            </button>
          </div>
        </div>

        <dl className="mt-6 grid gap-5 border-t border-slate-100 pt-5 sm:grid-cols-4">
          <Detail label="Recipient" value={order.recipientName} />
          <Detail label="Submitted" value={new Date(order.createdAt).toLocaleString()} />
          <Detail label="Current stage" value={stepLabel(order.sagaStep)} />
          <Detail label="Delivery" value={humanise(order.deliveryStatus)} />
        </dl>
      </div>

      <OrderTimeline order={order} />
    </div>
  );
}

function Detail({ label, value }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</dt>
      <dd className="mt-1 text-sm font-medium text-slate-900">{value || '—'}</dd>
    </div>
  );
}

function NewOrderModal({ onClose, onCreated }) {
  const [form, setForm] = useState({
    recipientName: '',
    deliveryAddress: '',
    packageDescription: '',
  });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // Escape closes the dialog, which is what anyone will try first.
  const handleKey = useCallback(
    (event) => {
      if (event.key === 'Escape') onClose();
    },
    [onClose],
  );

  useEffect(() => {
    document.addEventListener('keydown', handleKey);
    return () => document.removeEventListener('keydown', handleKey);
  }, [handleKey]);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);

    try {
      onCreated(await submitOrder(form));
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  const update = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink-900/60 p-5 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="New delivery order"
        className="w-full max-w-lg animate-rise rounded-2xl bg-white p-7 shadow-lift"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h3 className="text-lg font-bold text-slate-900">New delivery order</h3>
            <p className="mt-1 text-sm text-slate-500">
              This is sent to the middleware, which drives CMS, WMS and ROS in turn.
            </p>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
          >
            <CloseIcon />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <div>
            <label className="field-label" htmlFor="recipientName">
              Recipient name
            </label>
            <input
              id="recipientName"
              className="field"
              value={form.recipientName}
              onChange={update('recipientName')}
              placeholder="Nimal Perera"
              required
            />
          </div>

          <div>
            <label className="field-label" htmlFor="deliveryAddress">
              Delivery address
            </label>
            <input
              id="deliveryAddress"
              className="field"
              value={form.deliveryAddress}
              onChange={update('deliveryAddress')}
              placeholder="42 Galle Road, Colombo 03"
              required
            />
          </div>

          <div>
            <label className="field-label" htmlFor="packageDescription">
              Package description
            </label>
            <textarea
              id="packageDescription"
              rows={3}
              className="field resize-none"
              value={form.packageDescription}
              onChange={update('packageDescription')}
              placeholder="One laptop, 2.1 kg, fragile"
            />
          </div>

          {error && (
            <p role="alert" className="rounded-xl bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">
              {error}
            </p>
          )}

          <div className="flex gap-3 pt-2">
            <button type="button" className="btn-ghost flex-1" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn-primary flex-1" disabled={busy}>
              {busy ? 'Submitting…' : 'Submit order'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function Th({ children, className = '' }) {
  return (
    <th
      className={`px-5 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500 ${className}`}
    >
      {children}
    </th>
  );
}

function Dot() {
  return <span className="block h-2 w-2 rounded-full bg-current" />;
}
