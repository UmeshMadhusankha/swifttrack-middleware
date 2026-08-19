'use client';

import { badgeClasses } from '../lib/orderStages';

/**
 * One number in the row of metrics at the top of every dashboard.
 *
 * The accent colour is passed in rather than derived from the label, so the
 * same card works for "completed" on one screen and "delivered" on another.
 */
export default function StatCard({ label, value, accent = 'slate', hint, icon }) {
  const accents = {
    slate: 'bg-slate-100 text-slate-600',
    brand: 'bg-brand-50 text-brand-600',
    amber: 'bg-amber-50 text-amber-600',
    emerald: 'bg-emerald-50 text-emerald-600',
    rose: 'bg-rose-50 text-rose-600',
  };

  return (
    <div className="card p-5 transition hover:shadow-lift">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        {icon && (
          <span className={`flex h-9 w-9 items-center justify-center rounded-xl ${accents[accent]}`}>
            {icon}
          </span>
        )}
      </div>

      <p className="mt-3 text-4xl font-bold tracking-tight text-slate-900 tabular">{value}</p>
      {hint && <p className="mt-1.5 text-xs text-slate-400">{hint}</p>}
    </div>
  );
}

/** A coloured pill for a status. The colours are shared with every other view. */
export function StatusBadge({ status, label }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${badgeClasses(
        status,
      )}`}
    >
      {label || status}
    </span>
  );
}
