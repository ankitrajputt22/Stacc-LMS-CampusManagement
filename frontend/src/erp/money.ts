import type { FeeHead, PaymentMode } from '../types/fees'

const RUPEES = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2, minimumFractionDigits: 0 })

/** Rupees with Indian digit grouping, such as ₹1,12,500. */
export function rupees(amount: number): string {
  return RUPEES.format(Number(amount))
}

export const HEAD_WORDS: Record<FeeHead, string> = {
  TUITION: 'Tuition', EXAMINATION: 'Examination', LIBRARY: 'Library', LABORATORY: 'Laboratory', HOSTEL: 'Hostel', MESS: 'Mess', OTHER: 'Other',
}

export const MODE_WORDS: Record<PaymentMode, string> = {
  UPI: 'UPI', BANK_TRANSFER: 'Bank transfer', CARD: 'Card', CASH: 'Cash', CHEQUE: 'Cheque',
}
