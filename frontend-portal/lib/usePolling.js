'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Re-runs a fetch on a timer so a dashboard stays live without a refresh.
 *
 * Two things it is careful about. It never starts a second request while the
 * first is still running, which on a slow link would otherwise pile requests up
 * faster than they come back. And a failed poll leaves the last good data on
 * screen instead of blanking the dashboard: a dropped request is not news, and
 * the next tick three seconds later will probably succeed.
 *
 * @param load     async function returning the data
 * @param interval milliseconds between polls
 */
export function usePolling(load, interval = 3000) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [updatedAt, setUpdatedAt] = useState(null);

  // Kept in refs so the effect below can depend on nothing but the interval,
  // and so the timer is never torn down and rebuilt on every render.
  const loadRef = useRef(load);
  const inFlight = useRef(false);

  useEffect(() => {
    loadRef.current = load;
  }, [load]);

  const refresh = useCallback(async () => {
    if (inFlight.current) return;
    inFlight.current = true;

    try {
      const next = await loadRef.current();
      setData(next);
      setError('');
      setUpdatedAt(new Date());
    } catch (err) {
      setError(err.message);
    } finally {
      inFlight.current = false;
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, interval);
    return () => clearInterval(timer);
  }, [refresh, interval]);

  return { data, error, loading, updatedAt, refresh };
}
