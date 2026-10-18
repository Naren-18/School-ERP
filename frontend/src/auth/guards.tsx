import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Flex, Result, Spin } from 'antd'
import type { Role } from '../api/auth'
import { useAuth } from './useAuth'

/** Sends signed-out users to the login page, remembering where they were going. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (isLoading) {
    return (
      <Flex align="center" justify="center" style={{ minHeight: '100vh' }}>
        <Spin size="large" />
      </Flex>
    )
  }
  return children
}

/** Shows a 403 page unless the user has one of the given roles. */
export function RequireRole({ roles, children }: { roles: readonly Role[]; children: ReactNode }) {
  const { hasAnyRole } = useAuth()
  if (!hasAnyRole(roles)) {
    return <Result status="403" title="Not allowed" subTitle="You do not have access to this page." />
  }
  return children
}
