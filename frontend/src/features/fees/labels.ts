import type { PaymentMode } from '../../api/fees'

export const MODES: { value: PaymentMode; label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'UPI', label: 'UPI' },
  { value: 'CHEQUE', label: 'Cheque' },
  { value: 'CARD', label: 'Card' },
  { value: 'BANK', label: 'Bank transfer' },
  { value: 'ONLINE', label: 'Online' },
]

export const modeLabel = (m: PaymentMode) => MODES.find((x) => x.value === m)?.label ?? m
