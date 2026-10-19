import { api } from './client'

export interface ClassCount {
  classId: number
  className: string
  students: number
}

export interface DayPercent {
  date: string
  marked: number
  percentage: number | null
}

export interface AttendanceWidget {
  date: string
  /** set on holidays and weekly off days */
  nonWorkingReason: string | null
  enrolled: number
  present: number
  absent: number
  leave: number
  halfDay: number
  percentage: number | null
  sectionsToMark: number
  notMarked: { sectionId: number; label: string; students: number }[]
  trend: DayPercent[]
}

export interface FeesWidget {
  collectedToday: number
  collectedThisMonth: number
  outstanding: number
  overdue: number
  topOverdueClasses: { classId: number; className: string; students: number; overdue: number }[]
  trend: { date: string; amount: number }[]
}

export interface TeacherSection {
  sectionId: number
  label: string
  students: number
  classTeacher: boolean
  /** null when attendance is off or today is not a school day */
  marked: boolean | null
}

export interface MarksPending {
  examId: number
  examName: string
  examSubjectId: number
  sectionId: number
  label: string
  subjectName: string
  entered: number
  students: number
}

/** Shaped by role: widgets the user may not see, or whose module is off, are null. */
export interface Dashboard {
  /** null until the school has a current academic year */
  academicYearName: string | null
  students: { total: number; byClass: ClassCount[] } | null
  attendance: AttendanceWidget | null
  fees: FeesWidget | null
  upcomingExams: { id: number; name: string; startDate: string | null; endDate: string | null; published: boolean }[] | null
  recentAdmissions: { studentId: number; fullName: string; admissionNo: string; admissionDate: string; className: string | null }[] | null
  teacher: { sections: TeacherSection[]; nonWorkingReason: string | null; marksPending: MarksPending[] } | null
}

export const dashboardApi = {
  get: () => api.get<Dashboard>('/dashboard').then((r) => r.data),
}
