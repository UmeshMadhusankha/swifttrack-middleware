'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { login, saveSession } from '../lib/api';

/**
 * The login screen.
 *
 * It calls the gateway, which passes the request through to auth-service
 * untouched because /api/auth/** is the one route with no token check on it.
 * What comes back is a signed JWT that every later request carries.
 */
export default function LoginPage() {
  const router = useRouter();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);

    try {
      const session = await login(username, password);
      saveSession(session.token, session.username, session.role);
      router.push('/orders');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <div className="login-card">
        <div className="brand">Swift<span>Logistics</span></div>
        <p className="login-sub">Client delivery portal</p>

        <form className="panel" onSubmit={handleSubmit}>
          {error && <div className="error">{error}</div>}

          <label htmlFor="username">Username</label>
          <input
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
          />

          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />

          <button type="submit" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>

          <p className="hint">Prototype account: acme-corp / swift1234</p>
        </form>
      </div>
    </div>
  );
}
