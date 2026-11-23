import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError, UNAUTHORIZED_EVENT } from '../lib/api'
import type { StaffMember } from '../lib/types'

interface AuthState {
  /** undefined while the session is being checked; null when signed out. */
  staff: StaffMember | null | undefined
  login: (username: string, password: string) => Promise<StaffMember>
  logout: () => Promise<void>
  /** Call when an API request returns 401 (e.g. the session expired). */
  markSignedOut: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [staff, setStaff] = useState<StaffMember | null | undefined>(undefined)

  useEffect(() => {
    api
      .get<StaffMember | undefined>('/api/auth/me')
      .then((me) => setStaff(me ?? null))
      .catch((e: unknown) => {
        if (e instanceof ApiError && e.status !== 401) console.warn('Session check failed', e.message)
        setStaff(null)
      })
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const me = await api.post<StaffMember>('/api/auth/login', { username, password })
    setStaff(me)
    return me
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout')
    } finally {
      setStaff(null)
    }
  }, [])

  const markSignedOut = useCallback(() => setStaff(null), [])

  useEffect(() => {
    window.addEventListener(UNAUTHORIZED_EVENT, markSignedOut)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, markSignedOut)
  }, [markSignedOut])

  const value = useMemo(() => ({ staff, login, logout, markSignedOut }), [staff, login, logout, markSignedOut])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

/** The signed-in staff member; only call inside routes protected by RequireStaff. */
export function useStaff(): StaffMember {
  const { staff } = useAuth()
  if (!staff) throw new Error('useStaff called without a signed-in staff member')
  return staff
}
