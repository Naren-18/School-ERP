import { api } from './client'
import type { Page } from './users'

export type MessageType = 'ABSENCE' | 'FEE_REMINDER' | 'PAYMENT_RECEIVED' | 'RESULTS_PUBLISHED'
export type MessageStatus = 'SENT' | 'FAILED'

export const MESSAGE_TYPES: { value: MessageType; label: string }[] = [
  { value: 'ABSENCE', label: 'Absence alert' },
  { value: 'FEE_REMINDER', label: 'Fee reminder' },
  { value: 'PAYMENT_RECEIVED', label: 'Payment received' },
  { value: 'RESULTS_PUBLISHED', label: 'Results published' },
]

export const messageTypeLabel = (t: MessageType) => MESSAGE_TYPES.find((m) => m.value === t)?.label ?? t

/** What happened for one student when messages were sent. */
export interface Outcome {
  studentId: number
  studentName: string | null
  result: 'SENT' | 'FAILED' | 'SKIPPED'
  detail: string | null
}

export interface SendReport {
  type: MessageType
  /** False when the server has no WhatsApp keys: messages were only written to its log. */
  connected: boolean
  sent: number
  failed: number
  skipped: number
  outcomes: Outcome[]
}

/** The latest message tried for a student about the same thing (an absence day, an exam). */
export interface History {
  studentId: number
  status: MessageStatus
  at: string
}

export interface LogRow {
  id: number
  createdAt: string
  type: MessageType
  studentId: number
  studentName: string
  phone: string
  status: MessageStatus
  error: string | null
  sentByName: string | null
}

export interface LogParams {
  type?: MessageType
  status?: MessageStatus
  from?: string
  to?: string
  q?: string
  page: number
  size: number
}

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const notificationsApi = {
  status: () => data(api.get<{ connected: boolean }>('/notifications/status')),
  log: (params: LogParams) => data(api.get<Page<LogRow>>('/notifications', { params })),
}
