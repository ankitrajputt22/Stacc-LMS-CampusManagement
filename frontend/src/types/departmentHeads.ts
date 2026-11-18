export type DepartmentHeadTerm = {
  departmentHeadId: number
  userAccountId: number
  loginId: string
  name: string | null
  status: 'ACTIVE' | 'ENDED'
  startedAt: string
  endedAt: string | null
  counts: boolean
  notCountedReason: string | null
}

export type DepartmentHeadRow = {
  departmentId: number
  departmentCode: string
  departmentName: string
  departmentStatus: 'ACTIVE' | 'INACTIVE'
  head: DepartmentHeadTerm | null
}

export type DepartmentHeadCandidate = {
  userAccountId: number
  loginId: string
  name: string | null
}
