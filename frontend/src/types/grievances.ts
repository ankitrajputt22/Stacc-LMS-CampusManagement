// The grievance API exactly as the backend sends it (GrievanceService and GrievanceAdminService). Cases are
// confidential: the complainant's login ID reaches the committee only, and admins never read cases.

export type GrievanceStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'RESOLVED' | 'APPEALED' | 'CLOSED'

export type GrievanceCategoryOption = { categoryId: number; name: string; responseDays: number }

export type GrievanceSummary = {
  caseId: number
  caseNumber: string
  categoryName: string
  subject: string
  status: GrievanceStatus
  submittedAt: string
  /** The time the committee should answer by: the appeal's while appealed. */
  respondBy: string
  overdue: boolean
}

export type GrievanceCase = {
  caseId: number
  caseNumber: string
  categoryName: string
  subject: string
  /** Plain text, as are the resolution, appeal reason, and decision. */
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
  /** While resolved: the last moment to appeal. */
  appealUntil: string | null
  /** Sent to the committee only. */
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
