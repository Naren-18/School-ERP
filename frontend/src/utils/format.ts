import dayjs from 'dayjs'

/** Display format for dates across the app (CLAUDE.md: DD-MM-YYYY). */
export const DATE_FORMAT = 'DD-MM-YYYY'

/** The API's date format (ISO, as Java LocalDate serialises). */
export const API_DATE_FORMAT = 'YYYY-MM-DD'

export function formatDate(value: string | null | undefined): string {
  return value ? dayjs(value).format(DATE_FORMAT) : '—'
}

export function formatDateTime(value: string | null | undefined): string {
  return value ? dayjs(value).format(`${DATE_FORMAT} hh:mm A`) : '—'
}

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })

/** ₹1,23,456.00 */
export function formatINR(value: number | string | null | undefined): string {
  return value === null || value === undefined || value === '' ? '—' : inr.format(Number(value))
}
