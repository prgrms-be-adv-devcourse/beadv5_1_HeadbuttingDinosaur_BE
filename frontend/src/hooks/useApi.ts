import { useState, useEffect, useCallback, useRef } from 'react'

interface UseApiState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

interface UseApiOptions {
  immediate?: boolean   // mount 시 즉시 실행 (default: true)
  onSuccess?: (data: any) => void
  onError?: (err: string) => void
}

/**
 * API 호출 공통 훅
 *
 * @example
 * const { data, loading, error, execute } = useApi(
 *   () => getEvents({ page: 0, size: 12 }),
 * )
 */
export function useApi<T>(
  apiFn: () => Promise<{ data: { data: T } }>,
  options: UseApiOptions = {},
) {
  const { immediate = true, onSuccess, onError } = options
  const [state, setState] = useState<UseApiState<T>>({ data: null, loading: immediate, error: null })
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  const execute = useCallback(async () => {
    setState(s => ({ ...s, loading: true, error: null }))
    try {
      const res = await apiFn()
      const data = res.data.data
      if (mountedRef.current) {
        setState({ data, loading: false, error: null })
        onSuccess?.(data)
      }
      return data
    } catch (err: any) {
      const message = err?.response?.data?.message ?? err?.message ?? '오류가 발생했습니다'
      if (mountedRef.current) {
        setState(s => ({ ...s, loading: false, error: message }))
        onError?.(message)
      }
      throw err
    }
  }, [apiFn])

  useEffect(() => {
    if (immediate) execute()
  }, []) // eslint-disable-line

  const setData = useCallback((updater: (prev: T | null) => T) => {
    setState(s => ({ ...s, data: updater(s.data) }))
  }, [])

  return { ...state, execute, setData }
}

/**
 * 페이지네이션이 있는 목록 API 전용 훅
 *
 * @example
 * const { items, page, totalPages, loading, changePage, refresh } = usePagedApi(
 *   (p) => getEvents({ page: p, size: 12 })
 * )
 */
export function usePagedApi<T>(
  apiFn: (page: number) => Promise<{ data: { data: { content: T[]; totalPages: number; totalElements: number } } }>,
  initialPage = 0,
) {
  const [page, setPage] = useState(initialPage)
  const [items, setItems] = useState<T[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  const fetch = useCallback(async (p: number) => {
    setLoading(true)
    setError(null)
    try {
      const res = await apiFn(p)
      const { content, totalPages: tp, totalElements: te } = res.data.data
      if (mountedRef.current) {
        setItems(content)
        setTotalPages(tp)
        setTotalElements(te)
      }
    } catch (err: any) {
      const msg = err?.response?.data?.message ?? '로드 실패'
      if (mountedRef.current) setError(msg)
    } finally {
      if (mountedRef.current) setLoading(false)
    }
  }, [apiFn])

  useEffect(() => { fetch(page) }, [page])

  const changePage = useCallback((p: number) => setPage(p), [])
  const refresh = useCallback(() => fetch(page), [fetch, page])

  return { items, page, totalPages, totalElements, loading, error, changePage, refresh }
}
