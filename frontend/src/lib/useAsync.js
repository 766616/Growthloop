import { useCallback, useEffect, useRef, useState } from 'react'

const POLL_INTERVAL_MS = 60_000

/** Loads data on mount (and when deps change). Returns { data, loading, error, reload, setData }. */
export function useAsync(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null })
  const alive = useRef(true)
  const request = useRef(0)
  const run = useCallback(async ({ silent = false } = {}) => {
    const current = ++request.current
    if (!silent) setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const data = await fn()
      if (alive.current && current === request.current) setState({ data, loading: false, error: null })
      return data
    } catch (error) {
      if (alive.current && current === request.current) setState((s) => ({ ...s, loading: false, error }))
      return undefined
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  useEffect(() => {
    alive.current = true
    run()
    return () => { alive.current = false; request.current += 1 }
  }, [run])
  useEffect(() => {
    const refreshWhenVisible = () => {
      if (!document.hidden) run({ silent: true })
    }
    const reconnect = () => run({ silent: true })
    window.addEventListener('online', reconnect)
    document.addEventListener('visibilitychange', refreshWhenVisible)
    const poll = window.setInterval(() => {
      if (!document.hidden) run({ silent: true })
    }, POLL_INTERVAL_MS)
    return () => {
      window.clearInterval(poll)
      window.removeEventListener('online', reconnect)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
    }
  }, [run])
  const setData = (updater) => setState((s) => ({ ...s, data: typeof updater === 'function' ? updater(s.data) : updater }))
  return { ...state, reload: run, setData }
}
