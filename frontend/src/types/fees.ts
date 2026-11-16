// Fees as the API sends them (v0.16.7). Records only: payments are recorded by the accounts office.

export type FeeHead = 'TUITION' | 'EXAMINATION' | 'LIBRARY' | 'LABORATORY' | 'HOSTEL' | 'MESS' | 'OTHER'
export type PaymentMode = 'UPI' | 'BANK_TRANSFER' | 'CARD' | 'CASH' | 'CHEQUE'
export type AdjustmentKind = 'SCHOLARSHIP' | 'REFUND'
export type AdjustmentStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED'

export type FeeItem = { head: FeeHead; amount: number }

export type FeePayment = {
  paymentId: number
  receiptNumber: string
  amount: number
  mode: PaymentMode
  reference: string | null
  paidOn: string
  recordedAt: string
}

export type FeeAdjustment = {
  adjustmentId: number
  kind: AdjustmentKind
  studentLoginId: string
  academicSessionCode: string
  title: string
  amount: number
  reason: string
  status: AdjustmentStatus
  decisionComment: string | null
  createdAt: string
  decidedAt: string | null
}

/** A student's fees for one session; due is negative when more was paid than owed (credit is then refundable). */
export type FeeLedger = {
  studentLoginId: string
  rollNumber: string | null
  programCode: string
  programName: string
  academicSessionCode: string
  dueDate: string | null
  items: FeeItem[]
  total: number
  paid: number
  scholarships: number
  refunded: number
  due: number
  credit: number
  overdue: boolean
  payments: FeePayment[]
  adjustments: FeeAdjustment[]
}

export type FeeStructure = {
  programId: number
  programCode: string
  programName: string
  academicSessionId: number
  academicSessionCode: string
  dueDate: string | null
  items: FeeItem[]
  total: number
}
