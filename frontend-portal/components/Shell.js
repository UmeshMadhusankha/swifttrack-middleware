'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { BrandLock } from './Brand';
import { SignOutIcon } from './Icons';
import { clearSession, getToken, getUser } from '../lib/api';

/**
 * The frame every dashboard sits in: dark sidebar on the left, light content
 * area on the right.
 *
 * It also does the guarding. Anyone without a token is sent back to the login
 * page, and anyone whose token carries the wrong role is sent to their own
 * dashboard rather than being shown someone else's. This is a convenience, not
 * a security control: the real check is the gateway's, and every endpoint
 * behind it re-checks the role. Hiding a button has never stopped anyone.
 */
export default function Shell({ role, title, subtitle, sections, live, children }) {
  const router = useRouter();
  const [user, setUser] = useState(null);

  useEffect(() => {
    if (!getToken()) {
      router.replace('/');
      return;
    }

    const current = getUser();
    if (!current || current.role !== role) {
      router.replace('/');
      return;
    }
    setUser(current);
  }, [router, role]);

  function handleSignOut() {
    clearSession();
    router.replace('/');
  }

  // Render nothing until the guard above has decided, so a dashboard never
  // flashes on screen for a fraction of a second before the redirect.
  if (!user) return null;

  return (
    <div className="min-h-screen lg:flex">
      <Sidebar role={role} sections={sections} user={user} onSignOut={handleSignOut} />

      <main className="flex-1 min-w-0 bg-slate-100">
        <header className="border-b border-slate-200 bg-white/80 backdrop-blur">
          <div className="mx-auto max-w-[1600px] px-5 py-5 sm:px-8 sm:py-6">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-[1.75rem]">
                  {title}
                </h1>
                <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
              </div>
              {live}
            </div>
          </div>
        </header>

        <div className="mx-auto max-w-[1600px] px-5 py-6 sm:px-8 sm:py-8">{children}</div>
      </main>
    </div>
  );
}

function Sidebar({ role, sections, user, onSignOut }) {
  return (
    <aside className="lg:sticky lg:top-0 lg:h-screen lg:w-64 lg:shrink-0 bg-ink-900">
      <div className="flex h-full flex-col">
        <div className="px-6 py-6">
          <BrandLock tone="dark" />
          <p className="mt-3 text-[0.7rem] font-semibold uppercase tracking-[0.16em] text-ink-500">
            {role.toLowerCase()} workspace
          </p>
        </div>

        <nav className="hidden flex-1 space-y-1 px-3 lg:block">
          {sections.map((section, index) => (
            <a
              key={section.href}
              href={section.href}
              className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                index === 0
                  ? 'bg-ink-700 text-white'
                  : 'text-slate-400 hover:bg-ink-800 hover:text-slate-100'
              }`}
            >
              <span className="text-brand-300">{section.icon}</span>
              {section.label}
            </a>
          ))}
        </nav>

        <div className="mt-auto border-t border-ink-700 p-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-500 text-sm font-bold text-white">
              {user.username.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-white">{user.username}</p>
              <p className="text-xs text-ink-500">{user.role}</p>
            </div>
          </div>

          <button
            onClick={onSignOut}
            className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl border border-ink-600 px-3 py-2 text-sm font-medium text-slate-300 transition hover:bg-ink-800 hover:text-white"
          >
            <SignOutIcon />
            Sign out
          </button>
        </div>
      </div>
    </aside>
  );
}

/**
 * The "updating every 3 seconds" indicator in the page header.
 *
 * Worth showing: without it, a table that has not changed for a while is
 * indistinguishable from a table that has quietly stopped refreshing.
 */
export function LiveIndicator({ updatedAt, error }) {
  const [, tick] = useState(0);

  // Re-render once a second purely so "4s ago" keeps counting up.
  useEffect(() => {
    const timer = setInterval(() => tick((n) => n + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  if (error) {
    return (
      <span className="inline-flex items-center gap-2 rounded-full bg-rose-50 px-3.5 py-1.5 text-sm font-medium text-rose-700 ring-1 ring-inset ring-rose-600/20">
        <span className="h-2 w-2 rounded-full bg-rose-500" />
        Live updates interrupted
      </span>
    );
  }

  const seconds = updatedAt ? Math.round((Date.now() - updatedAt.getTime()) / 1000) : null;

  return (
    <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3.5 py-1.5 text-sm font-medium text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
      <span className="relative flex h-2 w-2">
        <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
        <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
      </span>
      Live
      <span className="text-emerald-600/70">
        {seconds === null ? 'connecting' : seconds <= 1 ? 'just now' : `${seconds}s ago`}
      </span>
    </span>
  );
}
