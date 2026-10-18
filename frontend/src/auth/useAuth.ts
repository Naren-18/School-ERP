import { createContext, useContext } from 'react'
import type { Me, Role } from '../api/auth'

export interface AuthState {
  isAuthenticated: boolean
  me: Me | undefined
  isLoading: boolean
  signIn: (token: string) => void
  signOut: () => void
  hasAnyRole: (roles: readonly Role[]) => boolean
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
