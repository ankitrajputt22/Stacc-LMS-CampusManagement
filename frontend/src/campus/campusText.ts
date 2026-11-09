import type { CampusNoticeStatus, CampusRequestStatus } from '../types/campus'

// The one place campus codes become words. Pages never show a raw code. Approval words come from
// approvals/approvalText: the request status and the approval status are shown separately.

const REQUEST: Record<CampusRequestStatus, string> = {
  SUBMITTED: 'Submitted',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
  COMPLETED: 'Completed',
}

const NOTICE: Record<CampusNoticeStatus, string> = { DRAFT: 'Draft', PUBLISHED: 'Published', ARCHIVED: 'Archived' }

export const REQUEST_STATUSES: readonly CampusRequestStatus[] = ['SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED']
export const NOTICE_STATUSES: readonly CampusNoticeStatus[] = ['DRAFT', 'PUBLISHED', 'ARCHIVED']

export const requestStatusLabel = (status: string) => REQUEST[status as CampusRequestStatus] ?? status
export const noticeStatusLabel = (status: string) => NOTICE[status as CampusNoticeStatus] ?? status

/** Who may use a service or read a notice, in the fixed order Students, Faculty, Admins. */
export function audienceLabel(student: boolean, faculty: boolean, admin = false): string {
  return [student && 'Students', faculty && 'Faculty', admin && 'Admins'].filter(Boolean).join(', ')
}

export const SUBJECT_MAX = 200
export const DETAILS_MAX = 10_000
export const SERVICE_CODE_MAX = 80
export const SERVICE_NAME_MAX = 150
export const SERVICE_DESCRIPTION_MAX = 3000
export const NOTICE_TITLE_MAX = 200
export const NOTICE_MESSAGE_MAX = 10_000
