// Admin user management as /api/v1/admin/users sends it. No answer ever carries a password or a hash.

export type { AccountRole as RoleName } from '../auth/roles'
import type { AccountRole as RoleName } from '../auth/roles'
export type AccountStatus = 'ACTIVE' | 'DISABLED'

export type UserSummary = {
  accountId: number
  loginId: string
  fullName: string | null
  email: string | null
  /** In the order STUDENT, FACULTY, ADMIN. */
  roles: RoleName[]
  status: AccountStatus
  passwordChangeRequired: boolean
}

export type UserPage = {
  items: UserSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type UserDetail = {
  accountId: number
  loginId: string
  fullName: string | null
  preferredName: string | null
  email: string | null
  roles: RoleName[]
  status: AccountStatus
  passwordChangeRequired: boolean
  temporaryPasswordExpiresAt: string | null
  createdAt: string | null
  studentProfile: {
    studentProfileId: number
    programCode: string
    programName: string
    admissionSessionCode: string
    rollNumber: string | null
    status: string
  } | null
  facultyProfile: { facultyProfileId: number; departmentCode: string; departmentName: string; status: string } | null
  /** Whether an official photo has been added. */
  hasPhoto: boolean
}

export type StudentProfileCreate = { programId: number; admissionSessionId: number; rollNumber: string | null }
export type FacultyProfileCreate = { departmentId: number }

export type UserCreate = {
  loginId: string
  fullName: string
  email: string | null
  roles: RoleName[]
  temporaryPassword: string
  studentProfile: StudentProfileCreate | null
  facultyProfile: FacultyProfileCreate | null
}

export type UserFilters = { role?: RoleName; status?: AccountStatus; q?: string; page: number; size: number }

export type ImportRow = { line: number; loginId: string; errors: string[] }
export type ImportResult = { dryRun: boolean; created: number; rowsWithErrors: number; rows: ImportRow[] }
