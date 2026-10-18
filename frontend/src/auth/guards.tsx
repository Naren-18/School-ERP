import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Flex, Result, Spin } from 'antd'
import type { Role } from '../api/auth'
import type { Modules } from '../api/school'
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

/** Shows a "switched off" page when the school has this module disabled in settings. */
export function RequireModule({ module, children }: { module: keyof Modules; children: ReactNode }) {
  const { me } = useAuth()
  if (me && !me.school.modules[module]) {
    return <Result status="404" title="Module switched off" subTitle="The principal can switch it on under Setup → Modules & preferences." />
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
