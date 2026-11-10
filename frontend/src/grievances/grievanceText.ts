import type { GrievanceStatus } from '../types/grievances'

export const GRIEVANCE_STATUS_LABEL: Record<GrievanceStatus, string> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  RESOLVED: 'Resolved',
  APPEALED: 'Appealed',
  CLOSED: 'Closed',
}
