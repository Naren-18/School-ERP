import { api } from './client'

export type Board = 'STATE' | 'CBSE' | 'ICSE' | 'OTHER'
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface Modules {
  attendance: boolean
  fees: boolean
  exams: boolean
  certificates: boolean
}

/** Which WhatsApp messages to parents the school sends. */
export interface Messages {
  absence: boolean
  feeReminder: boolean
  paymentReceived: boolean
  resultsPublished: boolean
}

export interface SchoolSettings {
  receiptPrefix: string
  certificatePrefix: string
  weekOffDays: DayOfWeek[]
  modules: Modules
  reportCard: { showRank: boolean; showAttendance: boolean }
  messages: Messages
}

export interface SchoolProfile {
  id: number
  code: string
  name: string
  board: Board
  medium: string
  address: string | null
  phone: string | null
  email: string | null
  logoUrl: string | null
  primaryColor: string
  settings: SchoolSettings
}

export type SchoolProfileInput = Pick<
  SchoolProfile,
  'name' | 'board' | 'medium' | 'address' | 'phone' | 'email' | 'primaryColor'
>

export const schoolApi = {
  get: () => api.get<SchoolProfile>('/school').then((r) => r.data),
  updateProfile: (body: SchoolProfileInput) => api.put<SchoolProfile>('/school/profile', body).then((r) => r.data),
  updateSettings: (body: SchoolSettings) => api.put<SchoolProfile>('/school/settings', body).then((r) => r.data),
  uploadLogo: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<SchoolProfile>('/school/logo', form).then((r) => r.data)
  },
}
