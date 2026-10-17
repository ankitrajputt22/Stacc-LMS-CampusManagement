// The academic masters as the admin API sends them. Codes, and what a record belongs to, never change after creation.

export type ActiveStatus = 'ACTIVE' | 'INACTIVE'
export type LifecycleStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED'

export type AdminDepartment = {
  id: number
  code: string
  name: string
  status: ActiveStatus
}

export type AdminProgram = {
  id: number
  departmentId: number
  departmentCode: string
  departmentName: string
  code: string
  name: string
  durationSemesters: number
  status: ActiveStatus
}

export type AdminAcademicSession = {
  id: number
  code: string
  /** A calendar date such as 2026-07-01. */
  startDate: string
  endDate: string
  status: LifecycleStatus
}

export type AdminSemester = {
  id: number
  programId: number
  programCode: string
  programName: string
  departmentCode: string
  academicSessionId: number
  academicSessionCode: string
  semesterNumber: number
  status: LifecycleStatus
}

export type AdminCourse = {
  id: number
  departmentId: number
  departmentCode: string
  departmentName: string
  code: string
  name: string
  /** An exact decimal with two places on the backend, such as 4.00 or 1.50. */
  credits: number
  status: ActiveStatus
}

export type DepartmentCreate = { code: string; name: string }
export type DepartmentUpdate = { name: string; status: ActiveStatus }
export type ProgramCreate = { departmentId: number; code: string; name: string; durationSemesters: number }
export type ProgramUpdate = { name: string; durationSemesters: number; status: ActiveStatus }
export type AcademicSessionCreate = { code: string; startDate: string; endDate: string }
export type AcademicSessionUpdate = { startDate: string; endDate: string; status: LifecycleStatus }
export type SemesterCreate = { programId: number; academicSessionId: number; semesterNumber: number }
export type SemesterUpdate = { status: LifecycleStatus }
export type CourseCreate = { departmentId: number; code: string; name: string; credits: number }
export type CourseUpdate = { name: string; credits: number; status: ActiveStatus }
