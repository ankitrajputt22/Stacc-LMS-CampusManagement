// The hostel API exactly as the backend sends it (HostelResponses and HostelRequests in com.stacc.backend.hostel).
// The approval status always comes from the approval engine; there is no hostel status of its own.

import type { ApprovalHistoryEntry, ApprovalStatus, ApprovalStep } from './approvals'

export type HostelAllocationStatus = 'ACTIVE' | 'VACATED'
export type BedOccupancy = 'AVAILABLE' | 'OCCUPIED' | 'INACTIVE'

export type EligibleHostelSession = {
  academicSessionId: number
  academicSessionCode: string
  startDate: string
  endDate: string
  sessionStatus: 'PLANNED' | 'ACTIVE' | 'CLOSED'
  hasCurrentApplication: boolean
}

export type StudentHostelAllocation = {
  hostelCode: string
  hostelName: string
  roomNumber: string
  bedNumber: string
  status: HostelAllocationStatus
  allocatedAt: string
  vacatedAt: string | null
}

export type HostelApplicationSummary = {
  applicationId: number
  applicationNumber: string
  academicSessionId: number
  academicSessionCode: string
  approvalStatus: ApprovalStatus
  submittedAt: string
  hasActiveAllocation: boolean
}

export type HostelApplicationDetail = {
  applicationId: number
  applicationNumber: string
  academicSessionId: number
  academicSessionCode: string
  note: string | null
  submittedAt: string
  approvalStatus: ApprovalStatus
  canCancel: boolean
  approvalSteps: ApprovalStep[]
  activeAllocation: StudentHostelAllocation | null
  pastAllocations: StudentHostelAllocation[]
}

export type HostelAllocation = {
  allocationId: number
  applicationId: number
  applicationNumber: string
  studentLoginId: string
  academicSessionCode: string
  hostelId: number
  hostelCode: string
  hostelName: string
  roomId: number
  roomNumber: string
  bedId: number
  bedNumber: string
  status: HostelAllocationStatus
  allocatedAt: string
  allocatedByLoginId: string
  vacatedAt: string | null
  vacatedByLoginId: string | null
}

export type HostelAdminApplicationSummary = {
  applicationId: number
  applicationNumber: string
  studentLoginId: string
  academicSessionId: number
  academicSessionCode: string
  approvalStatus: ApprovalStatus
  submittedAt: string
  activeAllocation: HostelAllocation | null
}

export type CurrentApprovalStep = {
  approvalRequestStepId: number
  stepOrder: number
  stepName: string
  requiredRoleCode: string
}

export type HostelAdminApplicationDetail = {
  applicationId: number
  applicationNumber: string
  studentLoginId: string
  academicSessionId: number
  academicSessionCode: string
  note: string | null
  submittedAt: string
  approvalStatus: ApprovalStatus
  approvalSteps: ApprovalStep[]
  approvalHistory: ApprovalHistoryEntry[]
  currentApprovalStep: CurrentApprovalStep | null
  canDecideCurrentStep: boolean
  canAllocate: boolean
  allocations: HostelAllocation[]
}

export type Page<T> = { items: T[]; page: number; size: number; totalElements: number; totalPages: number }

export type Hostel = {
  hostelId: number
  code: string
  name: string
  description: string | null
  active: boolean
  roomCount: number
  activeBedCount: number
  occupiedBedCount: number
  createdAt: string
  updatedAt: string
}

export type HostelRoom = {
  roomId: number
  hostelId: number
  roomNumber: string
  active: boolean
  activeBedCount: number
  occupiedBedCount: number
  numberLocked: boolean
}

export type HostelBed = {
  bedId: number
  roomId: number
  bedNumber: string
  active: boolean
  occupancy: BedOccupancy
  numberLocked: boolean
}

export type AvailableHostelBed = {
  bedId: number
  bedNumber: string
  roomId: number
  roomNumber: string
  hostelId: number
  hostelCode: string
  hostelName: string
}

export type ApplicationQueueQuery = {
  academicSessionId?: number
  approvalStatus?: ApprovalStatus
  studentLoginId?: string
  page: number
  size: number
}

export type AllocationQuery = {
  status?: HostelAllocationStatus
  hostelId?: number
  academicSessionId?: number
  page: number
  size: number
}
