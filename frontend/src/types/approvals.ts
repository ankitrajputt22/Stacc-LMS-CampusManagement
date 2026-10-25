// The parts of the approval engine (com.stacc.backend.approval) that pages show. Decisions go through the approval
// task routes; nothing here starts or cancels an approval.

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type ApprovalStepStatus = 'WAITING' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SKIPPED'

export type ApprovalStep = {
  stepOrder: number
  stepName: string
  requiredRoleCode: string
  status: ApprovalStepStatus
  actedByLoginId: string | null
  actedAt: string | null
  /** Plain text. */
  comment: string | null
}

export type ApprovalHistoryEntry = {
  action: string
  stepOrder: number | null
  actorLoginId: string | null
  comment: string | null
  createdAt: string
}

export type ApprovalDecision = {
  approvalRequestStepId: number
  approvalRequestId: number
  stepStatus: 'APPROVED' | 'REJECTED'
  requestStatus: ApprovalStatus
  currentStepOrder: number | null
}
