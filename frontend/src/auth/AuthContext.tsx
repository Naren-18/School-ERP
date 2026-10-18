import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { authApi } from '../api/auth'
import { tokenStore } from './tokenStore'
import { AuthContext, type AuthState } from './useAuth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [token, setToken] = useState(tokenStore.get())

  const meQuery = useQuery({
    queryKey: ['me', token],
    queryFn: authApi.me,
    enabled: token !== null,
    staleTime: Infinity,
    retry: false,
  })

  const signIn = useCallback((value: string) => {
    tokenStore.set(value)
    setToken(value)
  }, [])

  const signOut = useCallback(() => {
    tokenStore.clear()
    queryClient.clear()
    setToken(null)
  }, [queryClient])

  const value = useMemo<AuthState>(() => {
    const roles = meQuery.data?.roles ?? []
    return {
      isAuthenticated: token !== null,
      me: meQuery.data,
      isLoading: token !== null && meQuery.isPending,
      signIn,
      signOut,
      hasAnyRole: (allowed) => roles.some((role) => allowed.includes(role)),
    }
  }, [token, meQuery.data, meQuery.isPending, signIn, signOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

