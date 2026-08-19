'use client';

import { useMemo, useState } from 'react';
import Shell, { LiveIndicator } from '../../../components/Shell';
import StatCard, { StatusBadge } from '../../../components/StatCard';
import { TickIcon, CrossIcon } from '../../../components/Icons';
import { usePolling } from '../../../lib/usePolling';
import { fetchDeliveries, updateDeliveryStatus } from '../../../lib/api';
import { FAILURE_REASONS, humanise } from '../../../lib/orderStages';

/**
 * What a driver sees: the parcels the middleware has finished with, and two
 * buttons per parcel.
 *
 * The list is GET /api/orders/deliveries, which returns only orders whose saga
 * reached COMPLETED. Anything still moving through CMS, WMS or ROS is not a
 * job yet, and showing it would send someone out with a parcel the warehouse
 * has not reserved.
 */
export default function DriverDashboard() {
  const { data, error, updatedAt, refresh } = usePolling(fetchDeliveries, 3000);
  const deliveries = useMemo(() => data || [], [data]);

  const stats = useMemo(
    () => {
      const today = new Date().toDateString();

      return {
        // "Today" really means today: an undelivered parcel from yesterday
        // still belongs on the list below, but it is not today's workload.
        today: deliveries.filter((o) => new Date(o.createdAt).toDateString() === today).length,
        total: deliveries.length,
        pending: deliveries.filter((o) => o.deliveryStatus === 'PENDING_DELIVERY').length,
        delivered: deliveries.filter((o) => o.deliveryStatus === 'DELIVERED').length,
        failed: deliveries.filter((o) => o.deliveryStatus === 'DELIVERY_FAILED').length,
      };
    },
    [deliveries],
  );

  const sections = [
    { href: '#overview', label: 'Overview', icon: <Dot /> },
    { href: '#deliveries', label: 'My Deliveries', icon: <Dot /> },
  ];

  return (
    <Shell
      role="DRIVER"
      title="Driver Dashboard"
      subtitle="Parcels the middleware has processed and released for delivery"
      sections={sections}
      live={<LiveIndicator updatedAt={updatedAt} error={error} />}
    >
      <section id="overview" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Assigned today"
          value={stats.today}
          accent="brand"
          hint={`${stats.total} awaiting or completed in total`}
          icon={<Dot />}
        />
        <StatCard label="Pending" value={stats.pending} accent="amber" icon={<Dot />} />
        <StatCard label="Delivered" value={stats.delivered} accent="emerald" icon={<Dot />} />
        <StatCard label="Failed" value={stats.failed} accent="rose" icon={<Dot />} />
      </section>

      <section id="deliveries" className="mt-8">
        <h2 className="text-lg font-semibold text-slate-900">My Deliveries</h2>
        <p className="mb-4 text-sm text-slate-500">
          Mark each parcel once you have been to the address
        </p>

        {deliveries.length === 0 ? (
          <div className="card p-12 text-center">
            <p className="text-sm font-medium text-slate-900">Nothing to deliver</p>
            <p className="mt-1 text-sm text-slate-500">
              Orders appear here once the middleware has finished processing them.
            </p>
          </div>
        ) : (
          <div className="grid gap-4 xl:grid-cols-2">
            {deliveries.map((order) => (
              <DeliveryCard key={order.id} order={order} onUpdated={refresh} />
            ))}
          </div>
        )}
      </section>
    </Shell>
  );
}

function DeliveryCard({ order, onUpdated }) {
  const [reason, setReason] = useState(FAILURE_REASONS[0]);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');

  const settled = order.deliveryStatus !== 'PENDING_DELIVERY';

  async function mark(status) {
    setBusy(status);
    setError('');

    try {
      await updateDeliveryStatus(order.id, status, status === 'DELIVERY_FAILED' ? reason : null);
      await onUpdated();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy('');
    }
  }

  // A settled card is dimmed rather than removed: a driver who has just
  // tapped a button should still see the row they tapped.
  return (
    <article
      className={`card flex flex-col p-5 transition ${
        settled ? 'opacity-75' : 'hover:shadow-lift'
      }`}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wider text-brand-600">
            Order #{order.id}
          </p>
          <h3 className="mt-1 truncate text-base font-bold text-slate-900">
            {order.deliveryAddress}
          </h3>
        </div>

        <StatusBadge status={order.deliveryStatus} label={humanise(order.deliveryStatus)} />
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-4 border-t border-slate-100 pt-4">
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-slate-400">Customer</dt>
          <dd className="mt-1 truncate text-sm font-medium text-slate-900">
            {order.recipientName}
          </dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-slate-400">Package</dt>
          <dd className="mt-1 truncate text-sm font-medium text-slate-900">
            {order.packageDescription || '—'}
          </dd>
        </div>
      </dl>

      {order.deliveryStatusReason && (
        <p className="mt-4 rounded-xl bg-rose-50 px-4 py-2.5 text-sm text-rose-700">
          {order.deliveryStatusReason}
        </p>
      )}

      {error && (
        <p role="alert" className="mt-4 rounded-xl bg-rose-50 px-4 py-2.5 text-sm text-rose-700">
          {error}
        </p>
      )}

      {!settled && (
        <div className="mt-5 flex flex-col gap-3 border-t border-slate-100 pt-5">
          <button
            onClick={() => mark('DELIVERED')}
            disabled={busy !== ''}
            className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-emerald-700 focus:outline-none focus:ring-4 focus:ring-emerald-600/25 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <TickIcon className="h-4 w-4" />
            {busy === 'DELIVERED' ? 'Saving…' : 'Mark Delivered'}
          </button>

          <div className="flex gap-2">
            <select
              aria-label={`Failure reason for order ${order.id}`}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              className="min-w-0 flex-1 rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-700 transition focus:border-rose-500 focus:outline-none focus:ring-4 focus:ring-rose-500/10"
            >
              {FAILURE_REASONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>

            <button
              onClick={() => mark('DELIVERY_FAILED')}
              disabled={busy !== ''}
              className="inline-flex shrink-0 items-center justify-center gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-2.5 text-sm font-semibold text-rose-700 transition hover:bg-rose-100 focus:outline-none focus:ring-4 focus:ring-rose-500/15 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <CrossIcon className="h-4 w-4" />
              {busy === 'DELIVERY_FAILED' ? 'Saving…' : 'Mark Failed'}
            </button>
          </div>
        </div>
      )}
    </article>
  );
}

function Dot() {
  return <span className="block h-2 w-2 rounded-full bg-current" />;
}
