export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type ApprovalStepStatus = 'WAITING' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SKIPPED'

export type ApprovalStep = {
  stepOrder: number
  stepName: string
  requiredRoleCode: string
  status: ApprovalStepStatus
  actedByLoginId: string | null
  actedAt: string | null
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

export type ApprovalWorkflowSummary = {
  workflowId: number
  code: string
  name: string
  description: string | null
  active: boolean
  stepCount: number
  createdAt: string
  updatedAt: string
}
