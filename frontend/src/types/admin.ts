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

// Course offerings and official enrollments as the admin API sends them. A course offering is one course taught in
// one semester; its course and semester never change. Enrollments start ENROLLED and may end once, as COMPLETED or
// WITHDRAWN. A student appears only by profile ID, login ID, program, and admission session.

export type EnrollmentStatus = 'ENROLLED' | 'COMPLETED' | 'WITHDRAWN'

export type AdminCourseOffering = {
  courseOfferingId: number
  courseId: number
  courseCode: string
  courseName: string
  credits: number
  semesterId: number
  semesterNumber: number
  programId: number
  programCode: string
  academicSessionId: number
  academicSessionCode: string
  status: LifecycleStatus
}

export type CourseOfferingFilters = { semesterId?: number; academicSessionId?: number }
export type CourseOfferingCreate = { courseId: number; semesterId: number }
export type CourseOfferingUpdate = { status: LifecycleStatus }

export type EnrollmentStudent = {
  studentProfileId: number
  loginId: string
  programId: number
  programCode: string
  programName: string
  /** The session of original admission only, never a current session. */
  admissionSessionCode: string
  studentProfileStatus: ActiveStatus
}

export type AdminSemesterEnrollment = {
  semesterEnrollmentId: number
  studentProfileId: number
  semesterId: number
  semesterNumber: number
  programId: number
  programCode: string
  academicSessionId: number
  academicSessionCode: string
  status: EnrollmentStatus
}

export type StudentSemesterEnrollments = {
  student: EnrollmentStudent
  /** The latest session first. */
  semesterEnrollments: AdminSemesterEnrollment[]
}

export type AdminCourseEnrollment = {
  courseEnrollmentId: number
  semesterEnrollmentId: number
  courseOfferingId: number
  courseId: number
  courseCode: string
  courseName: string
  credits: number
  status: EnrollmentStatus
}

export type SemesterCourseEnrollments = {
  student: EnrollmentStudent
  semesterEnrollment: AdminSemesterEnrollment
  /** By course code. */
  courseEnrollments: AdminCourseEnrollment[]
}

/**
 * What happened on the LMS side once the official course enrollment was saved. The enrollment stands in every case.
 * NO_LMS_COURSE: the offering has no LMS course yet. FAILED: membership can be retried.
 */
export type LmsProvisioning = 'PROVISIONED' | 'NO_LMS_COURSE' | 'FAILED'

export type CourseEnrollmentCreated = {
  courseEnrollment: AdminCourseEnrollment
  lmsProvisioning: LmsProvisioning
}

export type RosterEntry = {
  courseEnrollmentId: number
  semesterEnrollmentId: number
  studentProfileId: number
  studentLoginId: string
  status: EnrollmentStatus
}

export type OfferingRoster = {
  courseOffering: AdminCourseOffering
  /** Every enrollment of this offering, whatever its status, by student login ID. */
  enrollments: RosterEntry[]
}
