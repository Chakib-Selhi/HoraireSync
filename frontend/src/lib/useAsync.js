import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Runs an async function when its dependencies change and tracks loading / error / data.
 * Results of a stale call (dependencies changed meanwhile) are ignored.
 */
export function useAsync(fn, deps, { enabled = true } = {}) {
  const [state, setState] = useState({ loading: enabled, error: null, data: null });
  const callId = useRef(0);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(fn, deps);

  useEffect(() => {
    if (!enabled) {
      setState({ loading: false, error: null, data: null });
      return;
    }
    const id = ++callId.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    run().then(
      (data) => id === callId.current && setState({ loading: false, error: null, data }),
      (error) => id === callId.current && setState({ loading: false, error, data: null }),
    );
  }, [run, enabled]);

  return state;
}
