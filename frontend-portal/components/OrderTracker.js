'use client';

import { STAGES, stagesCompleted, hasFailed, isUnwinding, isFinished } from '../lib/orderStages';

/**
 * The five-stage progress view.
 *
 * Each stage is one leg of the saga running behind the scenes. When a stage
 * turns green, a real legacy system has actually confirmed the work: CMS
 * raised an invoice, the warehouse acknowledged over its socket, ROS returned
 * a route.
 *
 * The failure case is treated as its own display rather than a red stage,
 * because a failed order is not "stuck part way" — the middleware has
 * deliberately undone everything it had already done.
 */
export default function OrderTracker({ order }) {
  const completed = stagesCompleted(order.status);
  const failed = hasFailed(order.status);
  const unwinding = isUnwinding(order.status);

  return (
    <div className="panel">
      <div className="status-head">
        <div>
          <h2 style={{ marginBottom: 2 }}>Order #{order.id}</h2>
          <div className="status-note">{order.recipientName} — {order.deliveryAddress}</div>
        </div>
        <StatusBadge status={order.status} />
      </div>

      {failed || unwinding ? (
        <UnwindNotice order={order} unwinding={unwinding} />
      ) : (
        <ol className="tracker">
          {STAGES.map((stage, index) => {
            const isDone = index < completed;
            const isActive = index === completed;
            const state = isDone ? 'done' : isActive ? 'active' : 'pending';

            return (
              <li className={`stage ${state}`} key={stage.key}>
                <div className="stage-rail">
                  <div className="dot">{isDone ? '✓' : ''}</div>
                  {index < STAGES.length - 1 && <div className="line" />}
                </div>
                <div className="stage-body">
                  <div className="stage-label">{stage.label}</div>
                  <div className="stage-detail">{stage.detail}</div>
                </div>
              </li>
            );
          })}
        </ol>
      )}

      {order.statusDetail && <div className="status-note">{order.statusDetail}</div>}

      {!isFinished(order.status) && (
        <p className="poll-note">Refreshing every 2 seconds…</p>
      )}
    </div>
  );
}

function StatusBadge({ status }) {
  const tone =
    status === 'COMPLETED' ? 'ok' :
    status === 'FAILED' ? 'bad' :
    status === 'COMPENSATING' ? 'warn' : 'running';

  return <span className={`badge ${tone}`}>{status}</span>;
}

/** Shown while the saga is rolling back, and after it has finished rolling back. */
function UnwindNotice({ order, unwinding }) {
  return (
    <div>
      <div className="stage failed">
        <div className="stage-rail"><div className="dot">!</div></div>
        <div className="stage-body">
          <div className="stage-label">
            {unwinding ? 'Rolling back completed steps' : 'Order could not be fulfilled'}
          </div>
          <div className="stage-detail">
            {unwinding
              ? 'A step failed. The middleware is undoing the work already done, newest first.'
              : 'Everything that had already succeeded was undone. No warehouse stock is held and no invoice stands.'}
          </div>
        </div>
      </div>
    </div>
  );
}
