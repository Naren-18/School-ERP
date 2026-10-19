import { api } from './client'
import type { Section } from './academic'

export interface Band {
  id?: number
  minPercent: number
  maxPercent: number
  grade: string
  gradePoint: number | null
  remark: string | null
}

export interface GradingScale {
  id: number
  name: string
  bands: Band[]
  inUse: boolean
}

export type Preset = 'CBSE_9_POINT' | 'TS_CCE'

export interface Exam {
  id: number
  academicYearId: number
  name: string
  gradingScaleId: number
  gradingScaleName: string
  startDate: string | null
  endDate: string | null
  displayOrder: number
  weightage: number | null
  published: boolean
  papers: number
}

export interface ExamInput {
  academicYearId: number
  name: string
  gradingScaleId: number
  startDate?: string | null
  endDate?: string | null
  displayOrder?: number
  weightage?: number | null
}

export interface Paper {
  id: number
  examId: number
  classId: number
  className: string
  subjectId: number
  subjectName: string
  maxMarks: number
  passMarks: number | null
  examDate: string | null
}

export interface EntryOption {
  examSubjectId: number
  sectionId: number
  classId: number
  className: string
  sectionName: string
  subjectId: number
  subjectName: string
  maxMarks: number
  students: number
  entered: number
}

export interface MarkRow {
  enrollmentId: number
  studentId: number
  rollNo: number | null
  fullName: string
  marks: number | null
  absent: boolean
  remarks: string | null
}

export interface MarkSheet {
  examSubjectId: number
  examName: string
  subjectName: string
  sectionId: number
  sectionLabel: string
  maxMarks: number
  passMarks: number | null
  editable: boolean
  lockedReason: string | null
  rows: MarkRow[]
}

export interface SubjectResult {
  subjectId: number
  subjectName: string
  maxMarks: number
  marks: number | null
  absent: boolean
  entered: boolean
  percent: number | null
  grade: string | null
  passed: boolean | null
}

export interface ExamResult {
  subjects: SubjectResult[]
  total: number
  maxTotal: number
  percent: number | null
  grade: string | null
  complete: boolean
  passed: boolean | null
}

export interface ResultSheet {
  examId: number
  examName: string
  published: boolean
  sectionId: number
  sectionLabel: string
  subjects: { subjectId: number; subjectName: string; maxMarks: number }[]
  rows: { enrollmentId: number; studentId: number; rollNo: number | null; fullName: string; result: ExamResult; rank: number | null }[]
}

export interface StudentResults {
  studentId: number
  fullName: string
  academicYearName: string | null
  exams: { examId: number; examName: string; published: boolean; weightage: number | null; result: ExamResult }[]
  finalResult: {
    percent: number | null
    grade: string | null
    passed: boolean | null
    subjectPercent: Record<string, number>
    subjectGrade: Record<string, string>
  }
  subjectNames: Record<string, string>
}

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const examsApi = {
  scales: () => data(api.get<GradingScale[]>('/exams/grading-scales')),
  addPreset: (preset: Preset) => data(api.post<GradingScale>(`/exams/grading-scales/presets/${preset}`)),
  saveScale: (id: number | null, body: { name: string; bands: Band[] }) =>
    data(id ? api.put<GradingScale>(`/exams/grading-scales/${id}`, body) : api.post<GradingScale>('/exams/grading-scales', body)),
  deleteScale: (id: number) => api.delete(`/exams/grading-scales/${id}`),

  list: (academicYearId?: number) => data(api.get<Exam[]>('/exams', { params: { academicYearId } })),
  save: (id: number | null, body: ExamInput) => data(id ? api.put<Exam>(`/exams/${id}`, body) : api.post<Exam>('/exams', body)),
  remove: (id: number) => api.delete(`/exams/${id}`),
  setPublished: (id: number, published: boolean) =>
    data(api.post<Exam>(`/exams/${id}/${published ? 'publish' : 'unpublish'}`)),

  papers: (examId: number, classId?: number) => data(api.get<Paper[]>(`/exams/${examId}/papers`, { params: { classId } })),
  savePaper: (examId: number, body: { classId: number; subjectId: number; maxMarks: number; passMarks?: number | null; examDate?: string | null }) =>
    data(api.put<Paper>(`/exams/${examId}/papers`, body)),
  applyPapers: (examId: number, body: { maxMarks: number; passMarks?: number | null; classIds?: number[] }) =>
    data(api.post<{ created: number }>(`/exams/${examId}/papers/apply`, body)),
  deletePaper: (paperId: number) => api.delete(`/exams/papers/${paperId}`),

  entryOptions: (examId: number) => data(api.get<EntryOption[]>(`/exams/${examId}/entry-options`)),
  sheet: (paperId: number, sectionId: number) =>
    data(api.get<MarkSheet>(`/exams/papers/${paperId}/marks`, { params: { sectionId } })),
  saveMarks: (paperId: number, sectionId: number, entries: { enrollmentId: number; marks: number | null; absent: boolean; remarks?: string | null }[]) =>
    data(api.put<MarkSheet>(`/exams/papers/${paperId}/marks`, { entries }, { params: { sectionId } })),

  results: (examId: number, sectionId: number) =>
    data(api.get<ResultSheet>(`/exams/${examId}/results`, { params: { sectionId } })),
  studentResults: (studentId: number) => data(api.get<StudentResults>(`/exams/students/${studentId}/results`)),
  reportCards: (params: { sectionId: number; examId?: number; studentId?: number }) =>
    data(api.get<Blob>('/exams/report-cards.pdf', { params, responseType: 'blob' })),
}

export const resultSections = (academicYearId?: number) =>
  data(api.get<Section[]>('/exams/result-sections', { params: { academicYearId } }))

export interface CoScholasticArea {
  id: number
  name: string
  displayOrder: number
}

export interface NoteRow {
  enrollmentId: number
  studentId: number
  rollNo: number | null
  fullName: string
  remark: string | null
  /** area id → grade */
  grades: Record<string, string>
}

export interface NoteSheet {
  examId: number
  examName: string
  sectionId: number
  sectionLabel: string
  areas: CoScholasticArea[]
  editable: boolean
  lockedReason: string | null
  rows: NoteRow[]
}

export const reportCardApi = {
  areas: () => data(api.get<CoScholasticArea[]>('/exams/co-scholastic-areas')),
  saveArea: (id: number | null, body: { name: string; displayOrder?: number }) =>
    data(id ? api.put<CoScholasticArea>(`/exams/co-scholastic-areas/${id}`, body) : api.post<CoScholasticArea>('/exams/co-scholastic-areas', body)),
  deleteArea: (id: number) => api.delete(`/exams/co-scholastic-areas/${id}`),
  notes: (examId: number, sectionId: number) => data(api.get<NoteSheet>(`/exams/${examId}/notes`, { params: { sectionId } })),
  saveNotes: (examId: number, sectionId: number, entries: { enrollmentId: number; remark: string | null; grades: Record<string, string> }[]) =>
    data(api.put<NoteSheet>(`/exams/${examId}/notes`, { entries }, { params: { sectionId } })),
}
