import { Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth } from './auth/guards'
import { LoginPage } from './features/auth/LoginPage'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { AppShell } from './layout/AppShell'
import { BrandTheme } from './layout/BrandTheme'

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
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrandTheme>
  )
}
