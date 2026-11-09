import { approvalStatusLabel, approvalTagClasses } from '../approvals/approvalText'
import type { ApprovalStatus } from '../types/approvals'
import type { CampusRequestStatus } from '../types/campus'
import { requestStatusLabel } from './campusText'

/**
 * The request status and, when the service needs approval, the approval status, as two separate words: "Submitted"
 * with "Approval: Pending" never reads as one status.
 */
export function RequestTags({ status, approvalStatus }: { status: CampusRequestStatus; approvalStatus: ApprovalStatus | null }) {
  return (
    <>
      <span className={approvalTagClasses(status)}>
        <span className="sr-only">Request status: </span>
        {requestStatusLabel(status)}
      </span>
      {approvalStatus && (
        <span className={approvalTagClasses(approvalStatus)}>Approval: {approvalStatusLabel(approvalStatus)}</span>
      )}
    </>
  )
}
