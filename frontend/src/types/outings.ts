export type OutingKind = 'GATE_PASS' | 'LEAVE'

export type OutingExtension = {
  extensionId: number
  requestedUntil: string
  reason: string
  approvalStatus: string
  createdAt: string
}

/** A hostel outing as the API works it out: approval, end (with approved extensions), and whether the student is out. */
export type HostelOuting = {
  outingId: number
  studentLoginId: string
  kind: OutingKind
  leaveFrom: string
  leaveUntil: string
  originalUntil: string
  reason: string
  destination: string | null
  approvalStatus: string
  returnedAt: string | null
  outside: boolean
  overdue: boolean
  canCancel: boolean
  canExtend: boolean
  canShowPass: boolean
  extensions: OutingExtension[]
}

/** A pass code, shown only now, and its window. */
export type OutingPass = { code: string; validFrom: string; validUntil: string }

export type PassCheck = {
  valid: boolean
  message: string
  studentLoginId: string | null
  hostel: string | null
  room: string | null
  kind: OutingKind | null
  leaveFrom: string | null
  leaveUntil: string | null
  outside: boolean
  outingId: number | null
}

export type GateMovement = {
  movementId: number
  studentLoginId: string
  direction: 'OUT' | 'IN'
  recordedAt: string
  recordedByLoginId: string
}

export type ApprovalTaskSummary = {
  approvalRequestStepId: number
  approvalRequestId: number
  workflowCode: string
  workflowName: string
  sourceType: string
  sourceId: string
  subject: string
  stepOrder: number
  stepName: string
  requiredRoleCode: string
  requesterLoginId: string
  submittedAt: string
}
