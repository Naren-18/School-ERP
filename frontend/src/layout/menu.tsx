import type { ReactNode } from 'react'
import {
  BankOutlined, BookOutlined, CalendarOutlined, CarryOutOutlined, CheckSquareOutlined, ClusterOutlined, ControlOutlined, DashboardOutlined,
  ReadOutlined, RiseOutlined, ScheduleOutlined, SettingOutlined, SolutionOutlined, TeamOutlined, UnorderedListOutlined,
  UploadOutlined, UserAddOutlined, TableOutlined, PieChartOutlined, WalletOutlined, DollarOutlined, FileTextOutlined,
  BarChartOutlined, ToolOutlined, ProfileOutlined, EditOutlined, TrophyOutlined,
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
export const TEACHING: readonly Role[] = ['PRINCIPAL', 'ADMIN', 'TEACHER']
export const FEE_STAFF: readonly Role[] = ['PRINCIPAL', 'ADMIN', 'ACCOUNTANT']

/** Sidebar entries; each is shown only to the listed roles. Later phases add modules here. */
export const MENU: readonly MenuEntry[] = [
  { path: '/', label: 'Dashboard', icon: <DashboardOutlined />, roles: ALL_ROLES },
  {
    path: '/students',
    label: 'Students',
    icon: <ReadOutlined />,
    roles: ALL_ROLES,
    children: [
      { path: '/students', label: 'All students', icon: <TeamOutlined />, roles: ALL_ROLES },
      { path: '/students/new', label: 'Admit student', icon: <UserAddOutlined />, roles: PRINCIPAL_ADMIN },
      { path: '/students/promotion', label: 'Promotion', icon: <RiseOutlined />, roles: PRINCIPAL_ADMIN },
      { path: '/students/import', label: 'Import CSV', icon: <UploadOutlined />, roles: PRINCIPAL_ADMIN },
    ],
  },
  {
    path: '/attendance',
    label: 'Attendance',
    icon: <CarryOutOutlined />,
    roles: TEACHING,
    module: 'attendance',
    children: [
      { path: '/attendance', label: 'Mark attendance', icon: <CheckSquareOutlined />, roles: TEACHING },
      { path: '/attendance/register', label: 'Monthly register', icon: <TableOutlined />, roles: TEACHING },
      { path: '/attendance/today', label: 'Summary', icon: <PieChartOutlined />, roles: PRINCIPAL_ADMIN },
    ],
  },
  {
    path: '/fees',
    label: 'Fees',
    icon: <WalletOutlined />,
    roles: FEE_STAFF,
    module: 'fees',
    children: [
      { path: '/fees/collect', label: 'Collect fee', icon: <DollarOutlined />, roles: FEE_STAFF },
      { path: '/fees/receipts', label: 'Receipts', icon: <FileTextOutlined />, roles: FEE_STAFF },
      { path: '/fees/reports', label: 'Reports', icon: <BarChartOutlined />, roles: FEE_STAFF },
      { path: '/fees/setup', label: 'Fee setup', icon: <ToolOutlined />, roles: FEE_STAFF },
    ],
  },
  {
    path: '/exams',
    label: 'Exams',
    icon: <ProfileOutlined />,
    roles: TEACHING,
    module: 'exams',
    children: [
      { path: '/exams', label: 'Enter marks', icon: <EditOutlined />, roles: TEACHING },
      { path: '/exams/results', label: 'Results & report cards', icon: <TrophyOutlined />, roles: TEACHING },
      { path: '/exams/setup', label: 'Exam setup', icon: <ToolOutlined />, roles: PRINCIPAL_ADMIN },
    ],
  },
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
