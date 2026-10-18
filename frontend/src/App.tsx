import type { ReactNode } from 'react'
import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import type { Role } from './api/auth'
import { RequireAuth, RequireModule, RequireRole } from './auth/guards'
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
import { PromotionPage } from './features/students/PromotionPage'
import { StudentFormPage } from './features/students/StudentFormPage'
import { StudentImportPage } from './features/students/StudentImportPage'
import { StudentListPage } from './features/students/StudentListPage'
import { StudentProfilePage } from './features/students/StudentProfilePage'
import { MarkAttendancePage } from './features/attendance/MarkAttendancePage'
import { RegisterPage } from './features/attendance/RegisterPage'
import { TodayPage } from './features/attendance/TodayPage'
import { CollectFeePage } from './features/fees/CollectFeePage'
import { FeeReportsPage } from './features/fees/FeeReportsPage'
import { FeeSetupPage } from './features/fees/FeeSetupPage'
import { ReceiptsPage } from './features/fees/ReceiptsPage'
import { ExamSetupPage } from './features/exams/ExamSetupPage'
import { MarksEntryPage } from './features/exams/MarksEntryPage'
import { ResultsPage } from './features/exams/ResultsPage'
import { AppShell } from './layout/AppShell'
import { BrandTheme } from './layout/BrandTheme'
import { FEE_STAFF, PRINCIPAL, PRINCIPAL_ADMIN, TEACHING } from './layout/menu'

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
          <Route path="students">
            <Route index element={<StudentListPage />} />
            <Route path="new" element={only(PRINCIPAL_ADMIN, <StudentFormPage />)} />
            <Route path="import" element={only(PRINCIPAL_ADMIN, <StudentImportPage />)} />
            <Route path="promotion" element={only(PRINCIPAL_ADMIN, <PromotionPage />)} />
            <Route path=":id" element={<StudentProfilePage />} />
            <Route path=":id/edit" element={only(PRINCIPAL_ADMIN, <StudentFormPage key="edit" />)} />
          </Route>
          <Route
            path="attendance"
            element={
              <RequireModule module="attendance">
                <Outlet />
              </RequireModule>
            }
          >
            <Route index element={only(TEACHING, <MarkAttendancePage />)} />
            <Route path="register" element={only(TEACHING, <RegisterPage />)} />
            <Route path="today" element={only(PRINCIPAL_ADMIN, <TodayPage />)} />
          </Route>
          <Route
            path="fees"
            element={
              <RequireModule module="fees">
                <Outlet />
              </RequireModule>
            }
          >
            <Route index element={<Navigate to="collect" replace />} />
            <Route path="collect" element={only(FEE_STAFF, <CollectFeePage />)} />
            <Route path="receipts" element={only(FEE_STAFF, <ReceiptsPage />)} />
            <Route path="reports" element={only(FEE_STAFF, <FeeReportsPage />)} />
            <Route path="setup" element={only(FEE_STAFF, <FeeSetupPage />)} />
          </Route>
          <Route
            path="exams"
            element={
              <RequireModule module="exams">
                <Outlet />
              </RequireModule>
            }
          >
            <Route index element={only(TEACHING, <MarksEntryPage />)} />
            <Route path="results" element={only(TEACHING, <ResultsPage />)} />
            <Route path="setup" element={only(PRINCIPAL_ADMIN, <ExamSetupPage />)} />
          </Route>
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
