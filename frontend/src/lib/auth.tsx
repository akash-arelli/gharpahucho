import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api, setUnauthorizedHandler, tokenStore } from './api'
import type { AuthResponse, Me } from './types'

interface AuthState {
  user: Me | null
  loading: boolean
  login: (email: string, password: string) => Promise<Me>
  register: (b: { email: string; password: string; displayName: string; homeCity?: string }) => Promise<Me>
  demoLogin: () => Promise<Me>
  logout: () => void
  setUser: (u: Me) => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const [user, setUser] = useState<Me | null>(null)
  const [loading, setLoading] = useState<boolean>(() => tokenStore.get() !== null)

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
    qc.clear()
  }, [qc])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  useEffect(() => {
    if (!tokenStore.get()) return
    let cancelled = false
    api
      .me()
      .then((me) => !cancelled && setUser(me))
      .catch(() => tokenStore.clear())
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  const accept = useCallback(
    (res: AuthResponse) => {
      qc.clear()
      tokenStore.set(res.token)
      setUser(res.user)
      return res.user
    },
    [qc],
  )

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      login: async (email, password) => accept(await api.login({ email, password })),
      register: async (b) => accept(await api.register(b)),
      demoLogin: async () => accept(await api.demoLogin()),
      logout,
      setUser,
    }),
    [user, loading, accept, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
