import type { ApprovalStatus, ApprovalStepStatus } from '../types/approvals'
import type { BedOccupancy, HostelAllocationStatus } from '../types/hostel'

// The one place hostel and approval codes become words. Pages never show a raw code.

const APPROVAL: Record<ApprovalStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
}

const STEP: Record<ApprovalStepStatus, string> = {
  WAITING: 'Waiting',
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SKIPPED: 'Skipped',
}

const OCCUPANCY: Record<BedOccupancy, string> = { AVAILABLE: 'Available', OCCUPIED: 'Occupied', INACTIVE: 'Inactive' }
const ALLOCATION: Record<HostelAllocationStatus, string> = { ACTIVE: 'Active', VACATED: 'Vacated' }

export const APPROVAL_STATUSES: readonly ApprovalStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED']

export const approvalStatusLabel = (status: string) => APPROVAL[status as ApprovalStatus] ?? status
export const stepStatusLabel = (status: string) => STEP[status as ApprovalStepStatus] ?? status
export const occupancyLabel = (occupancy: string) => OCCUPANCY[occupancy as BedOccupancy] ?? occupancy
export const allocationStatusLabel = (status: string) => ALLOCATION[status as HostelAllocationStatus] ?? status

export const NOTE_MAX = 3000
export const COMMENT_MAX = 5000
export const HOSTEL_CODE_MAX = 50
export const HOSTEL_NAME_MAX = 150
export const HOSTEL_DESCRIPTION_MAX = 2000
export const NUMBER_MAX = 50

export const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

/** A refused change in safe words: the backend's own message for 400, 404, and 409, never request details. */
export function refusalText(status: number | null, apiMessage: string | null, what = 'save'): string {
  switch (status) {
    case 400:
    case 404:
    case 409:
      return apiMessage ?? `Couldn’t ${what}. Try again.`
    case 403:
      return 'You don’t have access to do this. If your access changed recently, sign out and sign in again.'
    default:
      return `Couldn’t ${what}. Check your connection and try again.`
  }
}

const tag = 'inline-block shrink-0 rounded-sm border px-1.5 py-0.5 text-xs font-medium'

/** Quiet tag classes: the word carries the meaning, the border only supports it. */
export function approvalTagClasses(status: string): string {
  switch (status) {
    case 'APPROVED':
      return `${tag} border-success-line text-success`
    case 'REJECTED':
      return `${tag} border-danger/40 text-danger`
    case 'CANCELLED':
      return `${tag} border-line-strong text-ink-muted`
    default:
      return `${tag} border-line-strong text-ink`
  }
}

export function plainTagClasses(muted = false): string {
  return `${tag} border-line-strong ${muted ? 'text-ink-muted' : 'text-ink'}`
}
