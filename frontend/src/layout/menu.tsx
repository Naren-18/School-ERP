import type { ReactNode } from 'react'
import {
  BankOutlined, BookOutlined, CalendarOutlined, ClusterOutlined, ControlOutlined, DashboardOutlined,
  ScheduleOutlined, SettingOutlined, SolutionOutlined, TeamOutlined, UnorderedListOutlined,
} from '@ant-design/icons'
import type { Role } from '../api/auth'
import type { Modules } from '../api/school'

export interface MenuEntry {
  path: string
  label: string
  icon: ReactNode
  roles: readonly Role[]
  /** Hidden when the school has this module switched off. */
  module?: keyof Modules
  children?: readonly MenuEntry[]
}

export const ALL_ROLES: readonly Role[] = ['PRINCIPAL', 'ADMIN', 'ACCOUNTANT', 'TEACHER']
export const PRINCIPAL: readonly Role[] = ['PRINCIPAL']
export const PRINCIPAL_ADMIN: readonly Role[] = ['PRINCIPAL', 'ADMIN']

/** Sidebar entries; each is shown only to the listed roles. Later phases add modules here. */
export const MENU: readonly MenuEntry[] = [
  { path: '/', label: 'Dashboard', icon: <DashboardOutlined />, roles: ALL_ROLES },
  {
    path: '/settings',
    label: 'Setup',
    icon: <SettingOutlined />,
    roles: PRINCIPAL_ADMIN,
    children: [
      { path: '/settings/school', label: 'School profile', icon: <BankOutlined />, roles: PRINCIPAL },
      { path: '/settings/preferences', label: 'Modules & preferences', icon: <ControlOutlined />, roles: PRINCIPAL },
      { path: '/settings/years', label: 'Academic years', icon: <CalendarOutlined />, roles: PRINCIPAL_ADMIN },
      { path: '/settings/classes', label: 'Classes & sections', icon: <ClusterOutlined />, roles: PRINCIPAL_ADMIN },
      { path: '/settings/subjects', label: 'Subjects', icon: <BookOutlined />, roles: PRINCIPAL_ADMIN },
      {
        path: '/settings/class-subjects',
        label: 'Class subjects',
        icon: <UnorderedListOutlined />,
        roles: PRINCIPAL_ADMIN,
      },
      {
        path: '/settings/assignments',
        label: 'Teacher assignments',
        icon: <SolutionOutlined />,
        roles: PRINCIPAL_ADMIN,
      },
      { path: '/settings/holidays', label: 'Holidays', icon: <ScheduleOutlined />, roles: PRINCIPAL_ADMIN },
      { path: '/settings/users', label: 'Users', icon: <TeamOutlined />, roles: PRINCIPAL },
    ],
  },
]

/** Entries this user may see, given their roles and the school's enabled modules. */
export function visibleMenu(
  entries: readonly MenuEntry[],
  hasAnyRole: (roles: readonly Role[]) => boolean,
  modules: Modules,
): MenuEntry[] {
  return entries
    .filter((e) => hasAnyRole(e.roles) && (!e.module || modules[e.module]))
    .map((e) => (e.children ? { ...e, children: visibleMenu(e.children, hasAnyRole, modules) } : e))
    .filter((e) => !e.children || e.children.length > 0)
}
