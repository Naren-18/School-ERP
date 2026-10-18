import axios, { AxiosError } from 'axios'
import { tokenStore } from '../auth/tokenStore'

export const api = axios.create({ baseURL: '/api/v1' })

api.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const isLogin = error.config?.url === '/auth/login'
    if (error.response?.status === 401 && !isLogin) {
      tokenStore.clear()
      if (window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
    }
    return Promise.reject(error)
  },
)

interface ProblemDetail {
  detail?: string
  errors?: Record<string, string>
}

/** User-facing message from a failed request (backend ProblemDetail `detail`). */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ProblemDetail>(error)) {
    if (error.response?.data?.detail) {
      return error.response.data.detail
    }
    if (!error.response) {
      return 'Cannot reach the server. Check your connection and try again.'
    }
  }
  return 'Something went wrong. Please try again.'
}

/** Field-level validation messages from a 400 response, keyed by field name. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (axios.isAxiosError<ProblemDetail>(error) && error.response?.status === 400) {
    return error.response.data?.errors ?? {}
  }
  return {}
}

export function isNotFound(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 404
}
