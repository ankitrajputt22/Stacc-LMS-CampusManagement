import type { GrievanceStatus } from '../types/grievances'
import { GRIEVANCE_STATUS_LABEL } from './grievanceText'

const TONES: Record<GrievanceStatus, string> = {
  SUBMITTED: 'border-line-strong bg-surface-muted text-ink',
  UNDER_REVIEW: 'border-sky-300 bg-sky-50 text-portal-navy',
  RESOLVED: 'border-success-line bg-success-soft text-ink',
  APPEALED: 'border-sky-300 bg-sky-100 text-portal-navy',
  CLOSED: 'border-line-strong bg-surface-muted text-ink-muted',
}

export function GrievanceStatusTag({ status }: { status: GrievanceStatus }) {
  return (
    <span className={`inline-block shrink-0 rounded-sm border px-2 py-0.5 text-sm font-medium ${TONES[status]}`}>
      <span className="sr-only">Status: </span>
      {GRIEVANCE_STATUS_LABEL[status]}
    </span>
  )
}
