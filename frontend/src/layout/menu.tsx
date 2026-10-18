import type { ReactNode } from 'react'
import { DashboardOutlined } from '@ant-design/icons'
import type { Role } from '../api/auth'

export interface MenuEntry {
  path: string
  label: string
  icon: ReactNode
  roles: readonly Role[]
}

export const ALL_ROLES: readonly Role[] = ['PRINCIPAL', 'ADMIN', 'ACCOUNTANT', 'TEACHER']

/** Sidebar entries; each is shown only to the listed roles. Later phases add modules here. */
export const MENU: readonly MenuEntry[] = [
  { path: '/', label: 'Dashboard', icon: <DashboardOutlined />, roles: ALL_ROLES },
]
