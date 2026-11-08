import { useCallback, useEffect, useRef, useState } from 'react'
import { describeError } from '../services/api/http'

interface Polled<T> {
  data: T | null
  error: string | null
  loading: boolean
  refresh: () => Promise<void>
}

/**
 * Loads data and reloads it every `intervalMs` while `active`. Order state changes arrive asynchronously through
 * events (Spec 2.4), so the screens poll instead of expecting the HTTP response to carry the final state.
 */
export function usePolling<T>(load: () => Promise<T>, intervalMs: number, active = true): Polled<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const loadRef = useRef(load)
  loadRef.current = load

  const refresh = useCallback(async () => {
    try {
      setData(await loadRef.current())
      setError(null)
    } catch (err) {
      setError(describeError(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
    if (!active) return
    const id = window.setInterval(() => void refresh(), intervalMs)
    return () => window.clearInterval(id)
  }, [refresh, intervalMs, active])

  return { data, error, loading, refresh }
}
