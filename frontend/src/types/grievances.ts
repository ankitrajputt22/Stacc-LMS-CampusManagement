export type GrievanceStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'RESOLVED' | 'APPEALED' | 'CLOSED'

export type GrievanceCategoryOption = { categoryId: number; name: string; responseDays: number }

export type GrievanceSummary = {
  caseId: number
  caseNumber: string
  categoryName: string
  subject: string
  status: GrievanceStatus
  submittedAt: string
  respondBy: string
  overdue: boolean
}

export type GrievanceCase = {
  caseId: number
  caseNumber: string
  categoryName: string
  subject: string
  details: string
  status: GrievanceStatus
  submittedAt: string
  respondBy: string
  overdue: boolean
  reviewStartedAt: string | null
  resolution: string | null
  resolvedAt: string | null
  appealReason: string | null
  appealedAt: string | null
  appealRespondBy: string | null
  appealDecision: string | null
  closedAt: string | null
  appealUntil: string | null
  complainantLoginId: string | null
}

export type GrievanceCommitteeMember = { memberId: number; loginId: string; addedAt: string }
export type GrievanceCategoryAdmin = {
  categoryId: number
  code: string
  name: string
  responseDays: number
  active: boolean
  committee: GrievanceCommitteeMember[]
}
