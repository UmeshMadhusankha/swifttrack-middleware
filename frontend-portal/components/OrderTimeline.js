'use client';

import { TIMELINE, nodesCompleted, hasFailed, isUnwinding } from '../lib/orderStages';
import { TickIcon, CrossIcon } from './Icons';

/**
 * The order's journey through the middleware, drawn as a line of nodes.
 *
 * Horizontal from large screens up, vertical below that. A horizontal timeline
 * squeezed onto a phone turns into six unreadable labels, and this is the one
 * view a client actually looks at, so it gets both layouts rather than a
 * compromise that suits neither.
 *
 * Three states per node: done is filled and ticked, the current one pulses,
 * anything ahead is grey. A failed order marks the step it died on in red and
 * leaves the rest grey, because those steps never ran.
 */
export default function OrderTimeline({ order }) {
  const completed = nodesCompleted(order);
  const failed = hasFailed(order.status);
  const unwinding = isUnwinding(order.status);

  // The node the order died on is the one right after the last it finished.
  const failedIndex = failed || unwinding ? completed : -1;

  return (
    <div className="card p-6 sm:p-8">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-base font-semibold text-slate-900">Delivery progress</h3>
        <p className="text-sm text-slate-500">
          {failed
            ? 'Stopped — the middleware could not complete this order'
            : unwinding
              ? 'Rolling back the steps that had already succeeded'
              : `${Math.min(completed, TIMELINE.length)} of ${TIMELINE.length} steps complete`}
        </p>
      </div>

      {/* Horizontal, large screens up. */}
      <ol className="mt-8 hidden lg:flex">
        {TIMELINE.map((stage, index) => (
          <li key={stage.key} className="relative flex-1">
            <Connector
              index={index}
              total={TIMELINE.length}
              filled={index < completed}
              broken={index === failedIndex}
            />

            <div className="relative flex flex-col items-center px-2 text-center">
              <Node
                state={stateOf(index, completed, failedIndex)}
                number={index + 1}
              />
              <p
                className={`mt-3 text-sm font-semibold ${
                  index < completed ? 'text-slate-900' : 'text-slate-400'
                }`}
              >
                {stage.label}
              </p>
              <p className="mt-1 text-xs leading-relaxed text-slate-400">{stage.detail}</p>
            </div>
          </li>
        ))}
      </ol>

      {/* Vertical, below large screens. */}
      <ol className="mt-6 space-y-0 lg:hidden">
        {TIMELINE.map((stage, index) => (
          <li key={stage.key} className="relative flex gap-4 pb-6 last:pb-0">
            {index < TIMELINE.length - 1 && (
              <span
                className={`absolute left-[1.375rem] top-11 h-[calc(100%-1.75rem)] w-0.5 rounded ${
                  index < completed - 1 ? 'bg-brand-500' : 'bg-slate-200'
                }`}
              />
            )}

            <Node state={stateOf(index, completed, failedIndex)} number={index + 1} />

            <div className="pt-1.5">
              <p
                className={`text-sm font-semibold ${
                  index < completed ? 'text-slate-900' : 'text-slate-400'
                }`}
              >
                {stage.label}
              </p>
              <p className="mt-0.5 text-xs text-slate-400">{stage.detail}</p>
            </div>
          </li>
        ))}
      </ol>

      {order.statusDetail && (
        <p
          className={`mt-6 rounded-xl px-4 py-3 text-sm ${
            failed
              ? 'bg-rose-50 text-rose-700'
              : unwinding
                ? 'bg-orange-50 text-orange-700'
                : 'bg-slate-50 text-slate-600'
          }`}
        >
          {order.statusDetail}
        </p>
      )}
    </div>
  );
}

/** done | current | failed | future */
function stateOf(index, completed, failedIndex) {
  if (index === failedIndex) return 'failed';
  if (index < completed) return 'done';
  if (index === completed) return 'current';
  return 'future';
}

function Node({ state, number }) {
  if (state === 'failed') {
    return (
      <span className="relative z-10 flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-rose-500 text-white shadow-sm ring-4 ring-white">
        <CrossIcon className="h-6 w-6" />
      </span>
    );
  }

  if (state === 'done') {
    return (
      <span className="relative z-10 flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-brand-500 text-white shadow-sm ring-4 ring-white">
        <TickIcon className="h-6 w-6" />
      </span>
    );
  }

  if (state === 'current') {
    return (
      <span className="relative z-10 flex h-11 w-11 shrink-0 animate-beacon items-center justify-center rounded-full bg-white text-sm font-bold text-brand-600 shadow-sm ring-4 ring-white">
        <span className="absolute inset-0 rounded-full border-[3px] border-brand-500" />
        {number}
      </span>
    );
  }

  return (
    <span className="relative z-10 flex h-11 w-11 shrink-0 items-center justify-center rounded-full border-[3px] border-slate-200 bg-white text-sm font-bold text-slate-300 ring-4 ring-white">
      {number}
    </span>
  );
}

/** The line running between two nodes on the horizontal layout. */
function Connector({ index, total, filled, broken }) {
  if (index === total - 1) return null;

  return (
    <span
      className={`absolute left-1/2 top-[1.375rem] h-0.5 w-full rounded ${
        broken ? 'bg-rose-200' : filled ? 'bg-brand-500' : 'bg-slate-200'
      }`}
    />
  );
}
