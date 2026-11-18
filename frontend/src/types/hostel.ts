import type { ApprovalHistoryEntry, ApprovalStatus, ApprovalStep } from './approvals'

export type HostelAllocationStatus = 'ACTIVE' | 'VACATED'
export type BedOccupancy = 'AVAILABLE' | 'OCCUPIED' | 'MAINTENANCE' | 'INACTIVE'

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

export type HostelOccupancy = {
  hostelId: number
  code: string
  name: string
  active: boolean
  beds: number
  occupied: number
  maintenance: number
  available: number
}

export type BedMapCell = {
  bedId: number
  bedNumber: string
  state: BedOccupancy
  studentLoginId: string | null
  allocationId: number | null
  checkedIn: boolean
}

export type BedMapRoom = {
  roomId: number
  roomNumber: string
  block: string | null
  floorNumber: number | null
  roomType: string | null
  active: boolean
  beds: BedMapCell[]
}

export type HostelBedMap = { hostelId: number; code: string; name: string; rooms: BedMapRoom[] }

export type HostelWardenAssignment = {
  wardenAssignmentId: number
  hostelId: number
  loginId: string
  block: string | null
  assignedAt: string
  endedAt: string | null
}

export type HostelQueuePlace = { position: number | null; waiting: number }

export type RoomChangeSide = { loginId: string; hostel: string; room: string; bed: string }

export type RoomChange = {
  changeId: number
  kind: 'MOVE' | 'SWAP'
  requester: RoomChangeSide
  partner: RoomChangeSide | null
  partnerResponse: 'PENDING' | 'ACCEPTED' | 'DECLINED' | null
  reason: string
  preference: string | null
  status: 'OPEN' | 'COMPLETED' | 'CANCELLED' | 'DECLINED'
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | null
  createdAt: string
  completedAt: string | null
  mine: boolean
  canCancel: boolean
  canAnswer: boolean
  canComplete: boolean
}

export type RollCallStatus = 'OPEN' | 'FINALIZED'
export type RollCallMark = 'UNMARKED' | 'PRESENT' | 'ABSENT' | 'OUT'

export type RollCallSummary = {
  rollCallId: number
  hostelId: number
  hostelName: string
  block: string | null
  rollDate: string
  status: RollCallStatus
  residents: number
  present: number
  absent: number
  out: number
  unmarked: number
  finalizedAt: string | null
}

export type RollCallEntry = {
  entryId: number
  studentLoginId: string
  room: string
  bed: string
  status: RollCallMark
  prefilledOut: boolean
}

export type RollCallDetail = { summary: RollCallSummary; entries: RollCallEntry[] }

export type HostelNotice = {
  noticeId: number
  hostelId: number
  hostelName: string
  block: string | null
  title: string
  message: string
  emergency: boolean
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  publishedAt: string | null
  updatedAt: string
}

export type Meal = 'BREAKFAST' | 'LUNCH' | 'SNACKS' | 'DINNER'
export type MealLine = { meal: Meal; items: string; changed: boolean; note: string | null }
export type DayMenu = { date: string; meals: MealLine[] }
export type RatedMeal = { date: string; meal: Meal; rating: number }
export type ResidentMess = { hostelName: string | null; week: DayMenu[]; rated: RatedMeal[] }
export type MessTemplateLine = { dayOfWeek: number; meal: Meal; items: string }
export type MealSummary = { meal: Meal; ratings: number; average: number | null }
export type MealComment = { date: string; meal: Meal; rating: number; comment: string }
export type ManagerMess = {
  hostelId: number
  hostelName: string
  template: MessTemplateLine[]
  week: DayMenu[]
  lastMonth: MealSummary[]
  comments: MealComment[]
}

export type VisitStatus = 'EXPECTED' | 'ARRIVED' | 'LEFT' | 'CANCELLED'

export type HostelVisit = {
  visitId: number
  visitorName: string
  relation: string
  kind: 'PRE_REGISTERED' | 'WALK_IN'
  expectedOn: string
  status: VisitStatus
  arrivedAt: string | null
  leftAt: string | null
  residentLoginId: string
  hostel: string
  room: string
}

export type VisitingHours = { hostelId: number; opensAt: string; closesAt: string }
