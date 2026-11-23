import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from './api'

export interface AsyncState<T> {
  data: T | undefined
  error: ApiError | undefined
  loading: boolean
  /** Re-runs the loader; keeps showing the previous data while refreshing. */
  reload: () => Promise<void>
  setData: (data: T) => void
}

/** Loads data on mount and whenever `deps` change. Ignores responses from superseded requests. */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T>()
  const [error, setError] = useState<ApiError>()
  const [loading, setLoading] = useState(true)
  const requestId = useRef(0)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(loader, deps)

  const reload = useCallback(async () => {
    const id = ++requestId.current
    setLoading(true)
    try {
      const result = await run()
      if (id === requestId.current) {
        setData(result)
        setError(undefined)
      }
    } catch (e) {
      if (id === requestId.current) {
        setError(e instanceof ApiError ? e : new ApiError(0, 'UNKNOWN', 'Something went wrong.'))
      }
    } finally {
      if (id === requestId.current) setLoading(false)
    }
  }, [run])

  useEffect(() => {
    void reload()
  }, [reload])

  return { data, error, loading, reload, setData }
}
