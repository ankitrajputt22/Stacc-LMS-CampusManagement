// What the admin department head routes send. Names only, never contact details.

export type DepartmentHeadTerm = {
  departmentHeadId: number
  userAccountId: number
  loginId: string
  /** Null before the college records the person's name. */
  name: string | null
  status: 'ACTIVE' | 'ENDED'
  startedAt: string
  endedAt: string | null
  /** Whether an active term counts now: the account is active and still holds HOD. */
  counts: boolean
  /** Why an active term does not count, or null. */
  notCountedReason: string | null
}

export type DepartmentHeadRow = {
  departmentId: number
  departmentCode: string
  departmentName: string
  departmentStatus: 'ACTIVE' | 'INACTIVE'
  /** The active term, or null when the department has no head. */
  head: DepartmentHeadTerm | null
}

export type DepartmentHeadCandidate = {
  userAccountId: number
  loginId: string
  name: string | null
}
