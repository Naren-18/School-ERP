import { api } from './client'
import type { Messages, Modules } from './school'

export type Role = 'PRINCIPAL' | 'ADMIN' | 'ACCOUNTANT' | 'TEACHER'

export interface LoginRequest {
  schoolCode: string
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  expiresAt: string
}

export interface Branding {
  code: string
  name: string
  logoUrl: string | null
  primaryColor: string | null
}

export interface SchoolSummary extends Branding {
  id: number
  board: string
  medium: string
  modules: Modules
  messages: Messages
}

export interface Me {
  user: { id: number; username: string; fullName: string; designation: string | null }
  roles: Role[]
  school: SchoolSummary
}

export const authApi = {
  login: (body: LoginRequest) => api.post<LoginResponse>('/auth/login', body).then((r) => r.data),
  me: () => api.get<Me>('/auth/me').then((r) => r.data),
  branding: (code: string) =>
    api.get<Branding>(`/public/schools/${encodeURIComponent(code)}/branding`).then((r) => r.data),
}
