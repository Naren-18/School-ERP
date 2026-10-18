import { api } from './client'

export interface AcademicYear {
  id: number
  name: string
  startDate: string
  endDate: string
  current: boolean
}
export type AcademicYearInput = Pick<AcademicYear, 'name' | 'startDate' | 'endDate'>

export interface SchoolClass {
  id: number
  name: string
  displayOrder: number
}
export type SchoolClassInput = Omit<SchoolClass, 'id'>

export interface Section {
  id: number
  academicYearId: number
  classId: number
  className: string
  classOrder: number
  name: string
  classTeacherId: number | null
  classTeacherName: string | null
  capacity: number | null
}
export interface SectionInput {
  academicYearId: number
  classId: number
  name: string
  classTeacherId: number | null
  capacity: number | null
}

export type SubjectType = 'CORE' | 'LANGUAGE' | 'ELECTIVE' | 'CO_CURRICULAR'
export interface Subject {
  id: number
  name: string
  code: string | null
  type: SubjectType
}
export type SubjectInput = Omit<Subject, 'id'>

export interface ClassSubject {
  id: number
  subjectId: number
  subjectName: string
  subjectCode: string | null
  type: SubjectType
  displayOrder: number
}

export interface TeacherAssignment {
  id: number
  sectionId: number
  sectionName: string
  classId: number
  className: string
  subjectId: number
  subjectName: string
  teacherId: number
  teacherName: string
}

export interface Holiday {
  id: number
  academicYearId: number
  date: string
  description: string
}
export type HolidayInput = Omit<Holiday, 'id'>

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const academicApi = {
  years: () => data(api.get<AcademicYear[]>('/academic-years')),
  createYear: (body: AcademicYearInput) => data(api.post<AcademicYear>('/academic-years', body)),
  updateYear: (id: number, body: AcademicYearInput) => data(api.put<AcademicYear>(`/academic-years/${id}`, body)),
  makeCurrent: (id: number) => data(api.post<AcademicYear>(`/academic-years/${id}/make-current`)),
  deleteYear: (id: number) => api.delete(`/academic-years/${id}`),

  classes: () => data(api.get<SchoolClass[]>('/classes')),
  createClass: (body: SchoolClassInput) => data(api.post<SchoolClass>('/classes', body)),
  updateClass: (id: number, body: SchoolClassInput) => data(api.put<SchoolClass>(`/classes/${id}`, body)),
  deleteClass: (id: number) => api.delete(`/classes/${id}`),

  sections: (academicYearId?: number) =>
    data(api.get<Section[]>('/sections', { params: { academicYearId } })),
  createSection: (body: SectionInput) => data(api.post<Section>('/sections', body)),
  updateSection: (id: number, body: SectionInput) => data(api.put<Section>(`/sections/${id}`, body)),
  deleteSection: (id: number) => api.delete(`/sections/${id}`),

  subjects: () => data(api.get<Subject[]>('/subjects')),
  createSubject: (body: SubjectInput) => data(api.post<Subject>('/subjects', body)),
  updateSubject: (id: number, body: SubjectInput) => data(api.put<Subject>(`/subjects/${id}`, body)),
  deleteSubject: (id: number) => api.delete(`/subjects/${id}`),

  classSubjects: (academicYearId: number, classId: number) =>
    data(api.get<ClassSubject[]>('/class-subjects', { params: { academicYearId, classId } })),
  setClassSubjects: (body: { academicYearId: number; classId: number; subjectIds: number[] }) =>
    data(api.put<ClassSubject[]>('/class-subjects', body)),

  assignments: (params: { academicYearId?: number; sectionId?: number }) =>
    data(api.get<TeacherAssignment[]>('/teacher-assignments', { params })),
  assign: (body: { sectionId: number; subjectId: number; teacherId: number }) =>
    data(api.put<TeacherAssignment>('/teacher-assignments', body)),
  unassign: (id: number) => api.delete(`/teacher-assignments/${id}`),

  holidays: (academicYearId?: number) => data(api.get<Holiday[]>('/holidays', { params: { academicYearId } })),
  createHoliday: (body: HolidayInput) => data(api.post<Holiday>('/holidays', body)),
  updateHoliday: (id: number, body: HolidayInput) => data(api.put<Holiday>(`/holidays/${id}`, body)),
  deleteHoliday: (id: number) => api.delete(`/holidays/${id}`),
}
