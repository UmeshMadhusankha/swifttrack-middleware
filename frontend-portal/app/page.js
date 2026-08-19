'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Logo, Wordmark } from '../components/Brand';
import { homeForRole, login, roleFromToken, saveSession } from '../lib/api';

/**
 * The login screen.
 *
 * It calls the gateway, which passes the request through to auth-service
 * untouched because /api/auth/** is the one route with no token check on it.
 * What comes back is a signed JWT that every later request carries.
 *
 * Which dashboard you land on comes from the role claim inside that token, not
 * from the role picked in the selector above. The selector only fills in a
 * username to save typing during the demo; someone who picks "Admin" and then
 * signs in with the client account gets the client dashboard, because the
 * token is the only thing that decides.
 */

const ROLES = [
  { id: 'CLIENT', label: 'Client', username: 'client', blurb: 'Submit and track orders' },
  { id: 'DRIVER', label: 'Driver', username: 'driver', blurb: 'Complete deliveries' },
  { id: 'ADMIN', label: 'Admin', username: 'admin', blurb: 'Monitor the pipeline' },
];

export default function LoginPage() {
  const router = useRouter();

  const [selectedRole, setSelectedRole] = useState('CLIENT');
  const [username, setUsername] = useState('client');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  /** Picking a role pre-fills the username, but leaves the field editable. */
  function chooseRole(role) {
    setSelectedRole(role.id);
    setUsername(role.username);
    setError('');
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);

    try {
      const session = await login(username, password);
      saveSession(session.token, session.username, session.role);

      // Prefer the claim in the token itself; the response field is only a
      // convenience and the token is what every later request is judged on.
      const role = roleFromToken(session.token) || session.role;
      router.push(homeForRole(role));
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  const active = ROLES.find((role) => role.id === selectedRole);

  return (
    <div className="relative min-h-screen overflow-hidden bg-ink-900">
      {/* Two soft pools of colour so the navy is not a flat wall. */}
      <div className="pointer-events-none absolute -left-40 -top-40 h-[32rem] w-[32rem] rounded-full bg-brand-600/25 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-52 -right-32 h-[34rem] w-[34rem] rounded-full bg-brand-400/15 blur-3xl" />

      <div className="relative flex min-h-screen items-center justify-center px-5 py-12">
        <div className="w-full max-w-md animate-rise">
          <div className="flex flex-col items-center text-center">
            <Logo className="h-14 w-14" />
            <Wordmark tone="dark" className="mt-4 text-3xl" />
            <p className="mt-2 text-sm text-slate-400">
              Middleware portal for SwiftLogistics
            </p>
          </div>

          <div className="mt-8 rounded-2xl bg-white p-7 shadow-lift sm:p-8">
            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <span className="field-label">Sign in as</span>

                <div
                  role="radiogroup"
                  aria-label="Role"
                  className="grid grid-cols-3 gap-1.5 rounded-xl bg-slate-100 p-1.5"
                >
                  {ROLES.map((role) => {
                    const isActive = role.id === selectedRole;
                    return (
                      <button
                        key={role.id}
                        type="button"
                        role="radio"
                        aria-checked={isActive}
                        onClick={() => chooseRole(role)}
                        className={`rounded-lg px-2 py-2.5 text-sm font-semibold transition ${
                          isActive
                            ? 'bg-white text-brand-600 shadow-sm ring-1 ring-slate-900/5'
                            : 'text-slate-500 hover:text-slate-800'
                        }`}
                      >
                        {role.label}
                      </button>
                    );
                  })}
                </div>

                <p className="mt-2 text-xs text-slate-400">{active.blurb}</p>
              </div>

              <div>
                <label className="field-label" htmlFor="username">
                  Username
                </label>
                <input
                  id="username"
                  className="field"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  required
                />
              </div>

              <div>
                <label className="field-label" htmlFor="password">
                  Password
                </label>
                <input
                  id="password"
                  type="password"
                  className="field"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  placeholder="••••••••"
                  required
                />
              </div>

              {error && (
                <p
                  role="alert"
                  className="rounded-xl bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700"
                >
                  {error}
                </p>
              )}

              <button type="submit" className="btn-primary w-full py-3" disabled={busy}>
                {busy ? 'Signing in…' : 'Sign In'}
              </button>
            </form>

            <p className="mt-6 border-t border-slate-100 pt-5 text-center text-xs text-slate-400">
              Demo accounts: <span className="font-semibold text-slate-500">admin</span>,{' '}
              <span className="font-semibold text-slate-500">client</span>,{' '}
              <span className="font-semibold text-slate-500">driver</span> — password{' '}
              <span className="font-semibold text-slate-500">swift2026</span>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
