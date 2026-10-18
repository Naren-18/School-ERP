import { api } from './client'
import type { Page } from './users'

export type Gender = 'MALE' | 'FEMALE' | 'OTHER'
export type StudentStatus = 'ACTIVE' | 'LEFT' | 'TC_ISSUED' | 'PASSED_OUT'
export type SocialCategory = 'GEN' | 'OBC' | 'BC' | 'SC' | 'ST' | 'OTHER'
export type Relation = 'FATHER' | 'MOTHER' | 'GUARDIAN'
export type EnrollmentStatus = 'ACTIVE' | 'PROMOTED' | 'DETAINED' | 'LEFT'

export interface GuardianInput {
  relation: Relation
  fullName: string
  occupation?: string | null
  phone?: string | null
  email?: string | null
  primary?: boolean
}

export interface StudentDetailsInput {
  admissionNo: string
  admissionDate: string
  firstName: string
  lastName?: string | null
  dateOfBirth: string
  gender: Gender
  motherTongue?: string | null
  socialCategory?: SocialCategory | null
  religion?: string | null
  caste?: string | null
  /** Write-only: omit to keep, '' to remove. */
  aadhaar?: string
  identificationMarks?: string | null
  address?: string | null
  previousSchool?: string | null
  previousTcNo?: string | null
  guardians: GuardianInput[]
}

export interface Placement {
  sectionId: number
  rollNo?: number | null
  joinedOn?: string | null
}

export interface StudentListItem {
  id: number
  admissionNo: string
  fullName: string
  gender: Gender
  dateOfBirth: string
  status: StudentStatus
  enrollmentId: number | null
  sectionId: number | null
  className: string | null
  sectionName: string | null
  rollNo: number | null
  guardianName: string | null
  guardianPhone: string | null
  hasPhoto: boolean
}

export interface Guardian extends Required<Omit<GuardianInput, 'primary'>> {
  id: number
  primary: boolean
}

export interface EnrollmentView {
  id: number
  academicYearId: number
  academicYearName: string
  sectionId: number
  classId: number
  className: string
  sectionName: string
  rollNo: number | null
  status: EnrollmentStatus
  joinedOn: string
  leftOn: string | null
}

export interface StudentDetail {
  id: number
  admissionNo: string
  admissionDate: string
  firstName: string
  lastName: string | null
  fullName: string
  dateOfBirth: string
  gender: Gender
  motherTongue: string | null
  sensitiveVisible: boolean
  socialCategory: SocialCategory | null
  religion: string | null
  caste: string | null
  aadhaarMasked: string | null
  identificationMarks: string | null
  address: string | null
  previousSchool: string | null
  previousTcNo: string | null
  status: StudentStatus
  leftOn: string | null
  leavingReason: string | null
  hasPhoto: boolean
  photoVersion: number
  guardians: Guardian[]
  currentEnrollment: EnrollmentView | null
  enrollments: EnrollmentView[]
}

export interface SectionStudent {
  enrollmentId: number
  studentId: number
  admissionNo: string
  fullName: string
  gender: Gender
  rollNo: number | null
  status: EnrollmentStatus
  hasPhoto: boolean
}

export interface StudentSearch {
  q?: string
  academicYearId?: number
  classId?: number
  sectionId?: number
  status?: StudentStatus
  page: number
  size: number
}

export interface ImportReport {
  dryRun: boolean
  totalRows: number
  validRows: number
  created: number
  errors: { row: number; field: string; message: string }[]
}

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const studentsApi = {
  search: (params: StudentSearch) => data(api.get<Page<StudentListItem>>('/students', { params })),
  get: (id: number) => data(api.get<StudentDetail>(`/students/${id}`)),
  nextAdmissionNo: () => data(api.get<{ admissionNo: string }>('/students/next-admission-no')),
  create: (student: StudentDetailsInput, enrollment: Placement) =>
    data(api.post<StudentDetail>('/students', { student, enrollment })),
  update: (id: number, student: StudentDetailsInput) => data(api.put<StudentDetail>(`/students/${id}`, student)),
  leave: (id: number, body: { leftOn: string; reason: string }) =>
    data(api.post<StudentDetail>(`/students/${id}/leave`, body)),
  enroll: (id: number, body: Placement) => data(api.post<EnrollmentView>(`/students/${id}/enrollments`, body)),
  uploadPhoto: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return data(api.post<StudentDetail>(`/students/${id}/photo`, form))
  },
  photo: (id: number) => data(api.get<Blob>(`/students/${id}/photo`, { responseType: 'blob' })),

  changeSection: (enrollmentId: number, body: { sectionId: number; rollNo?: number | null }) =>
    data(api.put<EnrollmentView>(`/enrollments/${enrollmentId}/section`, body)),
  sectionStudents: (sectionId: number, includeInactive = false) =>
    data(api.get<SectionStudent[]>(`/sections/${sectionId}/students`, { params: { includeInactive } })),
  renumber: (sectionId: number) => data(api.post<SectionStudent[]>(`/sections/${sectionId}/renumber`)),
  promote: (body: {
    sourceSectionId: number
    targetSectionId: number
    promoteEnrollmentIds: number[]
    detainSectionId?: number
  }) => data(api.post<{ promoted: number; detained: number; placedInDetainSection: number }>('/promotions', body)),

  importTemplate: () => data(api.get<Blob>('/students/import/template', { responseType: 'blob' })),
  importCsv: (file: File, dryRun: boolean) => {
    const form = new FormData()
    form.append('file', file)
    return data(api.post<ImportReport>('/students/import', form, { params: { dryRun } }))
  },
}
