import type { Role } from './auth'
import { api } from './client'

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface User {
  id: number
  username: string
  fullName: string
  designation: string | null
  phone: string | null
  email: string | null
  roles: Role[]
  active: boolean
  lastLoginAt: string | null
}

export interface UserCreateInput {
  username: string
  fullName: string
  designation?: string
  phone?: string
  email?: string
  roles: Role[]
  password: string
}
export type UserUpdateInput = Omit<UserCreateInput, 'username' | 'password'>

export interface StaffOption {
  id: number
  fullName: string
  designation: string | null
  roles: Role[]
}

export interface UserSearch {
  q?: string
  role?: Role
  active?: boolean
  page: number
  size: number
}

export const usersApi = {
  search: (params: UserSearch) => api.get<Page<User>>('/users', { params }).then((r) => r.data),
  create: (body: UserCreateInput) => api.post<User>('/users', body).then((r) => r.data),
  update: (id: number, body: UserUpdateInput) => api.put<User>(`/users/${id}`, body).then((r) => r.data),
  resetPassword: (id: number, password: string) => api.post(`/users/${id}/reset-password`, { password }),
  setActive: (id: number, active: boolean) =>
    api.post<User>(`/users/${id}/${active ? 'activate' : 'deactivate'}`).then((r) => r.data),
  staff: (role?: Role) => api.get<StaffOption[]>('/staff', { params: { role } }).then((r) => r.data),
}
