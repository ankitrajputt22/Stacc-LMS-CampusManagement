// The campus API exactly as the backend sends it (CampusServiceResponses and CampusNoticeResponses in
// com.stacc.backend.campus). The request status is the business status; when a service needs approval, the approval
// status is the approval engine's and is sent separately. Requesters never see account details.

import type { ApprovalHistoryEntry, ApprovalStatus, ApprovalStep } from './approvals'

export type CampusRequestStatus = 'SUBMITTED' | 'APPROVED' | 'IN_PROGRESS' | 'READY' | 'REJECTED' | 'CANCELLED' | 'COMPLETED'
export type CampusNoticeStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export type Page<T> = { items: T[]; page: number; size: number; totalElements: number; totalPages: number }

/** A service the signed-in account may request. */
export type CampusService = {
  serviceId: number
  code: string
  name: string
  description: string | null
  approvalRequired: boolean
}

export type CampusRequestSummary = {
  requestId: number
  requestNumber: string
  serviceId: number
  serviceCode: string
  serviceName: string
  subject: string
  status: CampusRequestStatus
  approvalStatus: ApprovalStatus | null
  submittedAt: string
  completedAt: string | null
}

export type CampusRequestDetail = {
  requestId: number
  requestNumber: string
  serviceId: number
  serviceCode: string
  serviceName: string
  subject: string
  /** Plain text. */
  details: string
  status: CampusRequestStatus
  approvalStatus: ApprovalStatus | null
  /** The approver's comment, exactly as written, when the approval was rejected. */
  rejectionComment: string | null
  canCancel: boolean
  submittedAt: string
  completedAt: string | null
  approvalSteps: ApprovalStep[]
  processingAt: string | null
  readyAt: string | null
  /** The document issued with the request, kept in the requester's document vault. */
  issuedDocumentTitle: string | null
}

export type CampusNotice = {
  noticeId: number
  /** Plain text. */
  title: string
  /** Plain text. */
  message: string
  publishedAt: string
}

// --- Admin

export type AdminCampusService = {
  serviceId: number
  code: string
  name: string
  description: string | null
  active: boolean
  availableToStudent: boolean
  availableToFaculty: boolean
  approvalRequired: boolean
  approvalWorkflowCode: string | null
  createdAt: string
  updatedAt: string
}

export type CampusServiceSettings = {
  name: string
  description: string | null
  active: boolean
  availableToStudent: boolean
  availableToFaculty: boolean
  approvalRequired: boolean
  approvalWorkflowCode: string | null
}

export type AdminCampusRequestSummary = CampusRequestSummary & { requesterLoginId: string }

export type CurrentApprovalStep = {
  approvalRequestStepId: number
  stepOrder: number
  stepName: string
  requiredRoleCode: string
}

export type AdminCampusRequestDetail = {
  requestId: number
  requestNumber: string
  requesterLoginId: string
  serviceId: number
  serviceCode: string
  serviceName: string
  subject: string
  details: string
  status: CampusRequestStatus
  approvalStatus: ApprovalStatus | null
  rejectionComment: string | null
  submittedAt: string
  completedAt: string | null
  approvalSteps: ApprovalStep[]
  approvalHistory: ApprovalHistoryEntry[]
  currentApprovalStep: CurrentApprovalStep | null
  canDecide: boolean
  canComplete: boolean
  processingAt: string | null
  readyAt: string | null
  issuedDocumentTitle: string | null
  canProcess: boolean
  canMarkReady: boolean
}

export type AdminCampusNotice = {
  noticeId: number
  title: string
  message: string
  visibleToStudent: boolean
  visibleToFaculty: boolean
  visibleToAdmin: boolean
  status: CampusNoticeStatus
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export type CampusNoticeContent = {
  title: string
  message: string
  visibleToStudent: boolean
  visibleToFaculty: boolean
  visibleToAdmin: boolean
}

export type CampusRequestQuery = { status?: CampusRequestStatus; serviceId?: number; page: number; size: number }
export type AdminCampusRequestQuery = CampusRequestQuery & { requesterLoginId?: string }
