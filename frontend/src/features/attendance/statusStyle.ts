import type { AttendanceStatus } from '../../api/attendance'

/** Colours and labels for attendance marks, used by every attendance view. */
export const STATUS: Record<AttendanceStatus, { label: string; color: string; bg: string }> = {
  P: { label: 'Present', color: '#15803d', bg: '#dcfce7' },
  A: { label: 'Absent', color: '#b91c1c', bg: '#fee2e2' },
  L: { label: 'Leave', color: '#b45309', bg: '#fef3c7' },
  H: { label: 'Half day', color: '#1d4ed8', bg: '#dbeafe' },
}

export const NON_WORKING_BG = '#f1f5f9'

export const formatPercent = (p: number | null | undefined) => (p === null || p === undefined ? '—' : `${p}%`)
