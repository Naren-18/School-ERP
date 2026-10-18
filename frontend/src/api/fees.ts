import { api } from './client'
import type { Page } from './users'

export type PaymentMode = 'CASH' | 'UPI' | 'CHEQUE' | 'CARD' | 'BANK' | 'ONLINE'

export interface FeeHead {
  id: number
  name: string
  active: boolean
}

export interface Installment {
  id: number
  academicYearId: number
  classId: number
  className: string
  feeHeadId: number
  feeHeadName: string
  name: string
  amount: number
  dueDate: string
  students: number
}

export interface InstallmentInput {
  academicYearId: number
  classId: number
  feeHeadId: number
  name: string
  amount: number
  dueDate: string
}

export interface Due {
  id: number
  enrollmentId: number
  academicYearId: number
  academicYearName: string
  installmentId: number
  installmentName: string
  feeHeadName: string
  dueDate: string
  amount: number
  concession: number
  concessionReason: string | null
  paid: number
  balance: number
  overdue: boolean
}

export interface Totals {
  net: number
  paid: number
  balance: number
  overdue: number
}

export interface ReceiptSummary {
  id: number
  receiptNo: string
  paidOn: string
  studentId: number
  studentName: string
  admissionNo: string
  classLabel: string
  totalAmount: number
  paymentMode: PaymentMode
  cancelled: boolean
}

export interface StudentFees {
  studentId: number
  fullName: string
  admissionNo: string
  classLabel: string | null
  rollNo: number | null
  guardianName: string | null
  guardianPhone: string | null
  totals: Totals
  dues: Due[]
  receipts: ReceiptSummary[]
}

export interface Receipt {
  id: number
  receiptNo: string
  studentId: number
  studentName: string
  admissionNo: string
  classLabel: string
  academicYearName: string
  paidOn: string
  totalAmount: number
  amountInWords: string
  paymentMode: PaymentMode
  referenceNo: string | null
  collectedByName: string
  createdAt: string
  cancelled: boolean
  cancelReason: string | null
  cancelledByName: string | null
  cancelledAt: string | null
  lines: { dueId: number; installmentName: string; feeHeadName: string; amount: number }[]
}

export interface CollectInput {
  studentId: number
  paidOn?: string
  paymentMode: PaymentMode
  referenceNo?: string
  lines: { dueId: number; amount: number }[]
}

export interface CollectionReport {
  from: string
  to: string
  total: number
  receipts: number
  byMode: Partial<Record<PaymentMode, number>>
  days: { date: string; paymentMode: PaymentMode; receipts: number; total: number }[]
}

export interface DueRow {
  studentId: number
  admissionNo: string
  fullName: string
  classLabel: string
  rollNo: number | null
  guardianPhone: string | null
  net: number
  paid: number
  balance: number
  overdue: number
}

export interface ClassSummary {
  classId: number
  className: string
  students: number
  expected: number
  collected: number
  outstanding: number
  collectedPercent: number | null
}

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data)

export const feesApi = {
  heads: () => data(api.get<FeeHead[]>('/fees/heads')),
  createHead: (name: string) => data(api.post<FeeHead>('/fees/heads', { name })),
  updateHead: (id: number, body: { name: string; active: boolean }) => data(api.put<FeeHead>(`/fees/heads/${id}`, body)),
  deleteHead: (id: number) => api.delete(`/fees/heads/${id}`),

  plan: (academicYearId: number, classId?: number) =>
    data(api.get<Installment[]>('/fees/plan', { params: { academicYearId, classId } })),
  createInstallment: (body: InstallmentInput) => data(api.post<Installment>('/fees/plan', body)),
  updateInstallment: (id: number, body: InstallmentInput) => data(api.put<Installment>(`/fees/plan/${id}`, body)),
  deleteInstallment: (id: number) => api.delete(`/fees/plan/${id}`),
  copyPlan: (fromAcademicYearId: number, toAcademicYearId: number) =>
    data(api.post<{ copied: number; skipped: number }>('/fees/plan/copy', { fromAcademicYearId, toAcademicYearId })),
  generateDues: (academicYearId: number, classId?: number) =>
    data(api.post<{ created: number }>('/fees/dues/generate', { academicYearId, classId })),

  studentFees: (studentId: number) => data(api.get<StudentFees>(`/fees/students/${studentId}`)),
  setConcession: (dueId: number, concession: number, reason?: string) =>
    data(api.put<Due>(`/fees/dues/${dueId}/concession`, { concession, reason })),

  collect: (body: CollectInput) => data(api.post<Receipt>('/fees/receipts', body)),
  receipts: (params: { from?: string; to?: string; q?: string; cancelled?: boolean; page: number; size: number }) =>
    data(api.get<Page<ReceiptSummary>>('/fees/receipts', { params })),
  receipt: (id: number) => data(api.get<Receipt>(`/fees/receipts/${id}`)),
  receiptPdf: (id: number) => data(api.get<Blob>(`/fees/receipts/${id}/pdf`, { responseType: 'blob' })),
  cancel: (id: number, reason: string) => data(api.post<Receipt>(`/fees/receipts/${id}/cancel`, { reason })),

  collection: (from?: string, to?: string) =>
    data(api.get<CollectionReport>('/fees/reports/collection', { params: { from, to } })),
  dues: (params: { academicYearId?: number; classId?: number; sectionId?: number; overdueOnly?: boolean }) =>
    data(api.get<DueRow[]>('/fees/reports/dues', { params })),
  classSummary: (academicYearId?: number) =>
    data(api.get<ClassSummary[]>('/fees/reports/class-summary', { params: { academicYearId } })),
}
