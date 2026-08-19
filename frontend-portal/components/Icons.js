/**
 * The small marks used in the pipeline table and on cards.
 *
 * Each one carries a title so a screen reader announces "completed" rather
 * than reading nothing at all, which is what a bare coloured shape amounts to.
 */

export function TickIcon({ className = 'h-5 w-5' }) {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" className={className} role="img">
      <title>Completed</title>
      <path
        fillRule="evenodd"
        d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.137-.089l4-5.5z"
        clipRule="evenodd"
      />
    </svg>
  );
}

export function CrossIcon({ className = 'h-5 w-5' }) {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" className={className} role="img">
      <title>Failed</title>
      <path
        fillRule="evenodd"
        d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z"
        clipRule="evenodd"
      />
    </svg>
  );
}

/** A ring with one bright quarter, spun by CSS. Used for a step in flight. */
export function SpinnerIcon({ className = 'h-5 w-5' }) {
  return (
    <svg viewBox="0 0 20 20" fill="none" className={`animate-spin ${className}`} role="img">
      <title>In progress</title>
      <circle cx="10" cy="10" r="7.5" stroke="currentColor" strokeWidth="2.5" opacity="0.25" />
      <path
        d="M17.5 10a7.5 7.5 0 00-7.5-7.5"
        stroke="currentColor"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
    </svg>
  );
}

/** A hollow circle: this step has not been asked to do anything yet. */
export function WaitingIcon({ className = 'h-5 w-5' }) {
  return (
    <svg viewBox="0 0 20 20" fill="none" className={className} role="img">
      <title>Not started</title>
      <circle cx="10" cy="10" r="7" stroke="currentColor" strokeWidth="2" strokeDasharray="3 3" />
    </svg>
  );
}

export function PlusIcon({ className = 'h-4 w-4' }) {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" className={className} aria-hidden="true">
      <path d="M10 4a.75.75 0 01.75.75v4.5h4.5a.75.75 0 010 1.5h-4.5v4.5a.75.75 0 01-1.5 0v-4.5h-4.5a.75.75 0 010-1.5h4.5v-4.5A.75.75 0 0110 4z" />
    </svg>
  );
}

export function CloseIcon({ className = 'h-5 w-5' }) {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" className={className} aria-hidden="true">
      <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
    </svg>
  );
}

export function SignOutIcon({ className = 'h-4 w-4' }) {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" className={className} aria-hidden="true">
      <path
        fillRule="evenodd"
        d="M3 4.25A2.25 2.25 0 015.25 2h5.5A2.25 2.25 0 0113 4.25v2a.75.75 0 01-1.5 0v-2a.75.75 0 00-.75-.75h-5.5a.75.75 0 00-.75.75v11.5c0 .414.336.75.75.75h5.5a.75.75 0 00.75-.75v-2a.75.75 0 011.5 0v2A2.25 2.25 0 0110.75 18h-5.5A2.25 2.25 0 013 15.75V4.25z"
        clipRule="evenodd"
      />
      <path
        fillRule="evenodd"
        d="M6 10a.75.75 0 01.75-.75h9.19l-1.72-1.72a.75.75 0 111.06-1.06l3 3a.75.75 0 010 1.06l-3 3a.75.75 0 11-1.06-1.06l1.72-1.72H6.75A.75.75 0 016 10z"
        clipRule="evenodd"
      />
    </svg>
  );
}

/** The three system columns on the admin table share this cell renderer. */
export function SystemCell({ status }) {
  if (status === 'COMPLETED') {
    return <TickIcon className="h-5 w-5 text-emerald-500" />;
  }
  if (status === 'IN_PROGRESS') {
    return <SpinnerIcon className="h-5 w-5 text-amber-500" />;
  }
  if (status === 'FAILED') {
    return <CrossIcon className="h-5 w-5 text-rose-500" />;
  }
  return <WaitingIcon className="h-5 w-5 text-slate-300" />;
}
