/**
 * The SwiftTrack mark and wordmark.
 *
 * One component so the logo is identical on the login screen and in every
 * sidebar. The mark is inline SVG rather than an image file: it stays sharp at
 * any size, needs no network request, and takes its colour from the theme.
 */

/** The square mark: a chevron with two speed lines behind it. */
export function Logo({ className = 'h-10 w-10' }) {
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden="true">
      <defs>
        <linearGradient id="swifttrack-mark" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#4f86ff" />
          <stop offset="100%" stopColor="#1d4ed8" />
        </linearGradient>
      </defs>
      <rect width="40" height="40" rx="11" fill="url(#swifttrack-mark)" />
      <g
        stroke="white"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      >
        <path d="M18 13 L26 20 L18 27" />
        <path d="M9 16 H16" opacity="0.85" />
        <path d="M9 24 H14" opacity="0.55" />
      </g>
    </svg>
  );
}

/**
 * The wordmark.
 *
 * @param tone 'dark' on the navy sidebar, 'light' on a white background
 */
export function Wordmark({ tone = 'dark', className = 'text-xl' }) {
  const swift = tone === 'dark' ? 'text-white' : 'text-ink-900';
  const track = tone === 'dark' ? 'text-brand-300' : 'text-brand-500';

  return (
    <span className={`font-bold tracking-tight ${className}`}>
      <span className={swift}>Swift</span>
      <span className={track}>Track</span>
    </span>
  );
}

/** Mark and wordmark side by side, which is how the brand is normally shown. */
export function BrandLock({ tone = 'dark', logoClass = 'h-9 w-9', textClass = 'text-lg' }) {
  return (
    <div className="flex items-center gap-3">
      <Logo className={logoClass} />
      <Wordmark tone={tone} className={textClass} />
    </div>
  );
}
