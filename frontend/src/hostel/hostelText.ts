import type { BedOccupancy, HostelAllocationStatus } from '../types/hostel'

// The one place hostel codes become words. Pages never show a raw code. The approval words are shared with every
// module that shows approval progress and live in approvals/approvalText.

export {
  APPROVAL_STATUSES,
  approvalStatusLabel,
  approvalTagClasses,
  COMMENT_MAX,
  plainTagClasses,
  refusalText,
  SIGN_IN_AGAIN,
  stepStatusLabel,
} from '../approvals/approvalText'

const OCCUPANCY: Record<BedOccupancy, string> = { AVAILABLE: 'Available', OCCUPIED: 'Occupied', INACTIVE: 'Inactive' }
const ALLOCATION: Record<HostelAllocationStatus, string> = { ACTIVE: 'Active', VACATED: 'Vacated' }

export const occupancyLabel = (occupancy: string) => OCCUPANCY[occupancy as BedOccupancy] ?? occupancy
export const allocationStatusLabel = (status: string) => ALLOCATION[status as HostelAllocationStatus] ?? status

export const NOTE_MAX = 3000
export const HOSTEL_CODE_MAX = 50
export const HOSTEL_NAME_MAX = 150
export const HOSTEL_DESCRIPTION_MAX = 2000
export const NUMBER_MAX = 50
