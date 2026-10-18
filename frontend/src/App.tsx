import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import type { Role } from './api/auth'
import { RequireAuth, RequireRole } from './auth/guards'
import { LoginPage } from './features/auth/LoginPage'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { AcademicYearsPage } from './features/settings/AcademicYearsPage'
import { ClassesSectionsPage } from './features/settings/ClassesSectionsPage'
import { ClassSubjectsPage } from './features/settings/ClassSubjectsPage'
import { HolidaysPage } from './features/settings/HolidaysPage'
import { SchoolPreferencesPage } from './features/settings/SchoolPreferencesPage'
import { SchoolProfilePage } from './features/settings/SchoolProfilePage'
import { SubjectsPage } from './features/settings/SubjectsPage'
import { TeacherAssignmentsPage } from './features/settings/TeacherAssignmentsPage'
import { UsersPage } from './features/settings/UsersPage'
import { AppShell } from './layout/AppShell'
import { BrandTheme } from './layout/BrandTheme'
import { PRINCIPAL, PRINCIPAL_ADMIN } from './layout/menu'

const only = (roles: readonly Role[], page: ReactNode) => <RequireRole roles={roles}>{page}</RequireRole>

export default function App() {
  return (
    <BrandTheme>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <RequireAuth>
              <AppShell />
            </RequireAuth>
          }
        >
          <Route index element={<DashboardPage />} />
          <Route path="settings">
            <Route index element={<Navigate to="years" replace />} />
            <Route path="school" element={only(PRINCIPAL, <SchoolProfilePage />)} />
            <Route path="preferences" element={only(PRINCIPAL, <SchoolPreferencesPage />)} />
            <Route path="years" element={only(PRINCIPAL_ADMIN, <AcademicYearsPage />)} />
            <Route path="classes" element={only(PRINCIPAL_ADMIN, <ClassesSectionsPage />)} />
            <Route path="subjects" element={only(PRINCIPAL_ADMIN, <SubjectsPage />)} />
            <Route path="class-subjects" element={only(PRINCIPAL_ADMIN, <ClassSubjectsPage />)} />
            <Route path="assignments" element={only(PRINCIPAL_ADMIN, <TeacherAssignmentsPage />)} />
            <Route path="holidays" element={only(PRINCIPAL_ADMIN, <HolidaysPage />)} />
            <Route path="users" element={only(PRINCIPAL, <UsersPage />)} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrandTheme>
  )
}
