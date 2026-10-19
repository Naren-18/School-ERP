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
  async (error: AxiosError) => {
    // PDF requests use responseType 'blob', so their error body arrives as a Blob: read it back as ProblemDetail.
    const body = error.response?.data
    if (body instanceof Blob && body.type.includes('json')) {
      try {
        error.response!.data = JSON.parse(await body.text())
      } catch {
        // keep the Blob; errorMessage falls back to a generic message
      }
    }
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

/** Extra properties of a failed request's ProblemDetail (e.g. counts the screen can act on). */
export function problemProperty<T>(error: unknown, name: string): T | undefined {
  if (axios.isAxiosError<Record<string, unknown>>(error)) {
    return error.response?.data?.[name] as T | undefined
  }
  return undefined
}
