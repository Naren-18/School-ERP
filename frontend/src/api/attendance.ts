import { api } from './client'

export type AttendanceStatus = 'P' | 'A' | 'L' | 'H'

export interface Counts {
  present: number
  absent: number
  leave: number
  halfDay: number
  unmarked: number
  percentage: number | null
}

export interface SectionOption {
  sectionId: number
  classId: number
  className: string
  sectionName: string
  marked: boolean
}

export interface SheetStudent {
  enrollmentId: number
  studentId: number
  rollNo: number | null
  admissionNo: string
  fullName: string
  status: AttendanceStatus | null
}

export interface Sheet {
  sectionId: number
  sectionLabel: string
  date: string
  editable: boolean
  blockedReason: string | null
  marked: boolean
  markedByName: string | null
  markedAt: string | null
  counts: Counts
  students: SheetStudent[]
}

export interface Day {
  date: string
  dayOfWeek: string
  nonWorkingReason: string | null
  future: boolean
}

export interface StudentMonth {
  studentId: number
  fullName: string
  month: string
  days: Day[]
  statuses: (AttendanceStatus | null)[]
  counts: Counts
}

export interface RegisterRow {
  enrollmentId: number
  studentId: number
  rollNo: number | null
  fullName: string
  statuses: (AttendanceStatus | null)[]
  counts: Counts
}

export interface Register {
  sectionId: number
  sectionLabel: string
  month: string
  days: Day[]
  rows: RegisterRow[]
}

export interface Today {
  date: string
  nonWorkingReason: string | null
  enrolled: number
  counts: Counts
  classes: { classId: number; className: string; sections: number; markedSections: number; enrolled: number; counts: Counts }[]
  unmarkedSections: { sectionId: number; label: string; enrolled: number }[]
}

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const attendanceApi = {
  sections: (date?: string) => data(api.get<SectionOption[]>('/attendance/sections', { params: { date } })),
  sheet: (sectionId: number, date?: string) =>
    data(api.get<Sheet>(`/attendance/sections/${sectionId}`, { params: { date } })),
  save: (sectionId: number, date: string, entries: { enrollmentId: number; status: AttendanceStatus }[]) =>
    data(api.put<Sheet>(`/attendance/sections/${sectionId}`, { entries }, { params: { date } })),
  register: (sectionId: number, month: string) =>
    data(api.get<Register>(`/attendance/sections/${sectionId}/register`, { params: { month } })),
  registerCsv: (sectionId: number, month: string) =>
    data(api.get<Blob>(`/attendance/sections/${sectionId}/register.csv`, { params: { month }, responseType: 'blob' })),
  studentMonth: (studentId: number, month: string) =>
    data(api.get<StudentMonth>(`/attendance/students/${studentId}`, { params: { month } })),
  today: (date?: string) => data(api.get<Today>('/attendance/today', { params: { date } })),
}
