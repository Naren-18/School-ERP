import { api } from './client'
import type { Page } from './users'

export type CertificateType = 'BONAFIDE' | 'TC'
export type TemplateType = CertificateType | 'REPORT_CARD'

export interface IssuedCertificate {
  id: number
  studentId: number
  studentName: string
  admissionNo: string
  type: CertificateType
  title: string
  serialNo: string
  issuedOn: string
  issuedByName: string | null
  purpose: string | null
}

export interface TcDetails {
  leavingDate?: string | null
  reason?: string | null
  conduct?: string | null
  promotion?: string | null
  remarks?: string | null
}

export interface IssueRequest {
  type: CertificateType
  purpose?: string | null
  tc?: TcDetails | null
}

export interface TemplateView {
  type: TemplateType
  title: string
  html: string
  /** false while the school uses the built-in default */
  custom: boolean
  updatedAt: string | null
  /** {{key}} → description */
  values: Record<string, string>
  /** {{{key}}} → description */
  blocks: Record<string, string>
}

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const certificatesApi = {
  list: (params: { type?: CertificateType; from?: string; to?: string; q?: string; page?: number; size?: number }) =>
    data(api.get<Page<IssuedCertificate>>('/certificates', { params })),
  forStudent: (studentId: number) => data(api.get<IssuedCertificate[]>(`/certificates/students/${studentId}`)),
  preview: (studentId: number, body: IssueRequest) =>
    data(api.post<Blob>(`/certificates/students/${studentId}/preview`, body, { responseType: 'blob' })),
  issue: (studentId: number, body: IssueRequest) => data(api.post<IssuedCertificate>(`/certificates/students/${studentId}`, body)),
  pdf: (id: number) => data(api.get<Blob>(`/certificates/${id}/pdf`, { responseType: 'blob' })),
}

/** Template endpoints: certificates under /certificates/templates/{type}; the report card under /exams. */
const templateUrl = (type: TemplateType) =>
  type === 'REPORT_CARD' ? '/exams/report-card-template' : `/certificates/templates/${type}`

export const templatesApi = {
  get: (type: TemplateType) => data(api.get<TemplateView>(templateUrl(type))),
  save: (type: TemplateType, html: string) => data(api.put<TemplateView>(templateUrl(type), { html })),
  reset: (type: TemplateType) => data(api.delete<TemplateView>(templateUrl(type))),
  preview: (type: TemplateType, html: string) =>
    data(api.post<Blob>(`${templateUrl(type)}/preview`, { html }, { responseType: 'blob' })),
}
