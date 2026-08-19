'use client';

import { useMemo } from 'react';
import Shell, { LiveIndicator } from '../../../components/Shell';
import StatCard, { StatusBadge } from '../../../components/StatCard';
import { SystemCell } from '../../../components/Icons';
import { usePolling } from '../../../lib/usePolling';
import { fetchAllOrders } from '../../../lib/api';
import { hasFailed, humanise, isInProgress, isUnwinding, stepLabel } from '../../../lib/orderStages';

/**
 * The deep view of the middleware: every order in the system and exactly which
 * of the three legacy systems each one is sitting in.
 *
 * The data is GET /api/orders/all, which the order service refuses to anyone
 * whose token does not carry the ADMIN role. Polled every three seconds, so
 * the table walks an order through BILLING, STOCK_RESERVATION and
 * ROUTE_PLANNING while you watch.
 */
export default function AdminDashboard() {
  const { data, error, updatedAt } = usePolling(fetchAllOrders, 3000);
  const orders = useMemo(() => data || [], [data]);

  const stats = useMemo(() => {
    const today = new Date().toDateString();

    return {
      today: orders.filter((o) => new Date(o.createdAt).toDateString() === today).length,
      processing: orders.filter((o) => isInProgress(o.status)).length,
      completed: orders.filter((o) => o.status === 'COMPLETED').length,
      failed: orders.filter((o) => hasFailed(o.status)).length,
    };
  }, [orders]);

  const sections = [
    { href: '#overview', label: 'Overview', icon: <Dot /> },
    { href: '#pipeline', label: 'Live Pipeline', icon: <Dot /> },
  ];

  return (
    <Shell
      role="ADMIN"
      title="System Dashboard"
      subtitle="Every order in the middleware, and the SAGA step it is on right now"
      sections={sections}
      live={<LiveIndicator updatedAt={updatedAt} error={error} />}
    >
      <section id="overview" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Orders today" value={stats.today} accent="brand" icon={<Dot />} />
        <StatCard
          label="Processing now"
          value={stats.processing}
          accent="amber"
          hint="Still moving through CMS, WMS or ROS"
          icon={<Dot />}
        />
        <StatCard label="Completed" value={stats.completed} accent="emerald" icon={<Dot />} />
        <StatCard label="Failed" value={stats.failed} accent="rose" icon={<Dot />} />
      </section>

      <section id="pipeline" className="mt-8">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Live Order Pipeline</h2>
            <p className="text-sm text-slate-500">
              Refreshing every 3 seconds — rows in flight are highlighted
            </p>
          </div>

          <Legend />
        </div>

        <PipelineTable orders={orders} />
      </section>
    </Shell>
  );
}

function PipelineTable({ orders }) {
  if (orders.length === 0) {
    return (
      <div className="card p-12 text-center">
        <p className="text-sm font-medium text-slate-900">No orders in the system</p>
        <p className="mt-1 text-sm text-slate-500">
          Submit one from the client dashboard and it will appear here within three seconds.
        </p>
      </div>
    );
  }

  return (
    <div className="card overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[1120px] text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50/80 text-left">
              <Th>Order</Th>
              <Th>Client</Th>
              <Th>Submitted</Th>
              <Th>Current status</Th>
              <Th>Current SAGA step</Th>
              <Th className="text-center">CMS</Th>
              <Th className="text-center">WMS</Th>
              <Th className="text-center">ROS</Th>
              <Th>Delivery</Th>
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {orders.map((order) => (
              <PipelineRow key={order.id} order={order} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function PipelineRow({ order }) {
  const failed = hasFailed(order.status);
  const unwinding = isUnwinding(order.status);
  const inFlight = isInProgress(order.status) && !failed && !unwinding;

  /*
   * An order mid-flight gets a soft amber row and an order that has broken
   * gets a red one, so both jump out of a long table without anyone having to
   * read the status column. Everything else stays plain white; highlighting
   * the settled majority would make the highlight meaningless.
   */
  const rowTone = failed
    ? 'bg-rose-50/70 hover:bg-rose-50'
    : unwinding
      ? 'bg-orange-50/70 hover:bg-orange-50'
      : inFlight
        ? 'bg-amber-50/70 hover:bg-amber-50'
        : 'hover:bg-slate-50';

  return (
    <tr className={`transition-colors ${rowTone}`}>
      <td className="px-4 py-4">
        <span className="font-semibold text-slate-900 tabular">#{order.id}</span>
        <p className="mt-0.5 max-w-[12rem] truncate text-xs text-slate-400">
          {order.deliveryAddress}
        </p>
      </td>

      <td className="px-4 py-4 text-slate-600">{order.clientId}</td>

      <td className="px-4 py-4 text-slate-500 tabular">
        {new Date(order.createdAt).toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        })}
      </td>

      <td className="px-4 py-4">
        <StatusBadge status={order.status} label={humanise(order.status)} />
      </td>

      <td className="px-4 py-4">
        <span
          className={`inline-flex items-center gap-2 text-sm font-medium ${
            failed ? 'text-rose-700' : inFlight ? 'text-amber-700' : 'text-slate-600'
          }`}
        >
          {inFlight && (
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-amber-400 opacity-75" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-amber-500" />
            </span>
          )}
          {stepLabel(order.sagaStep)}
        </span>
      </td>

      <SystemTd status={order.cmsStatus} />
      <SystemTd status={order.wmsStatus} />
      <SystemTd status={order.rosStatus} />

      <td className="px-4 py-4">
        <StatusBadge status={order.deliveryStatus} label={humanise(order.deliveryStatus)} />
        {order.deliveryStatusReason && (
          <p className="mt-1 max-w-[10rem] truncate text-xs text-slate-400">
            {order.deliveryStatusReason}
          </p>
        )}
      </td>
    </tr>
  );
}

function SystemTd({ status }) {
  return (
    <td className="px-4 py-4">
      <div className="flex justify-center">
        <SystemCell status={status} />
      </div>
    </td>
  );
}

function Legend() {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-slate-500">
      <LegendItem className="bg-emerald-500" label="Completed" />
      <LegendItem className="bg-amber-500" label="In progress" />
      <LegendItem className="bg-rose-500" label="Failed" />
      <LegendItem className="bg-slate-300" label="Not started" />
    </div>
  );
}

function LegendItem({ className, label }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={`h-2.5 w-2.5 rounded-full ${className}`} />
      {label}
    </span>
  );
}

function Th({ children, className = '' }) {
  return (
    <th
      className={`whitespace-nowrap px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-500 ${className}`}
    >
      {children}
    </th>
  );
}

function Dot() {
  return <span className="block h-2 w-2 rounded-full bg-current" />;
}
