import React, { createContext, useContext, useEffect, useState, useCallback } from 'react'
import { getProfile } from '@/api/auth.api'
import type { GetProfileResponse } from '@/api/types'

interface AuthState {
  user: GetProfileResponse | null
  isLoggedIn: boolean
  isLoading: boolean
  role: 'USER' | 'SELLER' | 'ADMIN' | null
}

interface AuthContextValue extends AuthState {
  login: (accessToken: string, refreshToken: string) => Promise<void>
  logout: () => void
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    isLoggedIn: false,
    isLoading: true,
    role: null,
  })

  const fetchUser = useCallback(async () => {
    const token = localStorage.getItem('accessToken')
    if (!token) {
      setState({ user: null, isLoggedIn: false, isLoading: false, role: null })
      return
    }
    try {
      const res = await getProfile()
      const user = res.data
      setState({
        user,
        isLoggedIn: true,
        isLoading: false,
        role: user.role as AuthState['role'],
      })
    } catch {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      setState({ user: null, isLoggedIn: false, isLoading: false, role: null })
    }
  }, [])

  useEffect(() => { fetchUser() }, [fetchUser])

  const login = useCallback(async (accessToken: string, refreshToken: string) => {
    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    await fetchUser()
  }, [fetchUser])

  const logout = useCallback(() => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    setState({ user: null, isLoggedIn: false, isLoading: false, role: null })
  }, [])

  return (
    <AuthContext.Provider value={{ ...state, login, logout, refresh: fetchUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be inside AuthProvider')
  return ctx
}
