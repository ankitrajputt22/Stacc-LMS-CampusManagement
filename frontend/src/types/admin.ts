// The academic masters as the admin API sends them. Codes, and what a record belongs to, never change after creation.

export type ActiveStatus = 'ACTIVE' | 'INACTIVE'
export type DegreeType = 'UNDERGRADUATE' | 'POSTGRADUATE' | 'DIPLOMA' | 'DOCTORATE' | 'CERTIFICATE'
export type CourseType = 'CORE' | 'ELECTIVE' | 'LAB' | 'PROJECT' | 'AUDIT'
export type LifecycleStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED'

export type AdminDepartment = {
  id: number
  code: string
  name: string
  /** Plain text, or null when none was entered. */
  description: string | null
  status: ActiveStatus
}

export type AdminProgram = {
  id: number
  departmentId: number
  departmentCode: string
  departmentName: string
  code: string
  name: string
  degreeType: DegreeType
  durationSemesters: number
  status: ActiveStatus
  /** The most credits a student may register for in one semester; null means no limit. */
  maxCreditsPerSemester: number | null
}

export type AdminAcademicSession = {
  id: number
  code: string
  /** A calendar date such as 2026-07-01. */
  startDate: string
  endDate: string
  status: LifecycleStatus
  /** The college's current session. At most one is. */
  current: boolean
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
  /** Registration period and drop deadline as UTC moments; null when not set. */
  registrationOpensAt: string | null
  registrationClosesAt: string | null
  dropDeadline: string | null
}

export type AdminCourse = {
  id: number
  departmentId: number
  departmentCode: string
  departmentName: string
  code: string
  name: string
  shortName: string | null
  description: string | null
  /** An exact decimal with two places on the backend, such as 4.00 or 1.50. */
  credits: number
  courseType: CourseType
  status: ActiveStatus
}

/** A student section (A, B, …) of one semester. */
export type AdminSection = {
  id: number
  semesterId: number
  semesterNumber: number
  programId: number
  programCode: string
  academicSessionId: number
  academicSessionCode: string
  code: string
  name: string | null
  /** The most enrolled students it holds, or null for no limit. */
  capacity: number | null
  /** How many students are enrolled in it now. */
  enrolledCount: number
  status: ActiveStatus
}

export type SectionCreate = { semesterId: number; code: string; name: string | null; capacity: number | null }
export type SectionUpdate = { name: string | null; capacity: number | null; status: ActiveStatus }

export type DepartmentCreate = { code: string; name: string; description: string | null }
export type DepartmentUpdate = { name: string; status: ActiveStatus; description: string | null }
type ProgramLimits = { maxCreditsPerSemester: number | null }
export type ProgramCreate = {
  departmentId: number
  code: string
  name: string
  durationSemesters: number
  degreeType: DegreeType
} & ProgramLimits
export type ProgramUpdate = { name: string; durationSemesters: number; status: ActiveStatus; degreeType: DegreeType } & ProgramLimits
export type AcademicSessionCreate = { code: string; startDate: string; endDate: string }
export type AcademicSessionUpdate = { startDate: string; endDate: string; status: LifecycleStatus }
export type SemesterCreate = { programId: number; academicSessionId: number; semesterNumber: number }
export type SemesterUpdate = { status: LifecycleStatus }
/** Moments with a zone, such as 2026-07-01T03:30:00.000Z. All null clears the period. */
export type SemesterRegistrationUpdate = {
  registrationOpensAt: string | null
  registrationClosesAt: string | null
  dropDeadline: string | null
}
type CourseDetails = { shortName: string | null; description: string | null; courseType: CourseType }
export type CourseCreate = { departmentId: number; code: string; name: string; credits: number } & CourseDetails
export type CourseUpdate = { name: string; credits: number; status: ActiveStatus } & CourseDetails

// Course offerings and official enrollments as the admin API sends them. A course offering is one course taught in
// one semester; its course and semester never change. Enrollments start ENROLLED and may end once, as COMPLETED or
// WITHDRAWN; a course enrollment may also be DROPPED by the student. A student appears only by profile ID, login ID, program, and admission session.

export type EnrollmentStatus = 'ENROLLED' | 'COMPLETED' | 'WITHDRAWN' | 'DROPPED'

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
  /** The section it is for, or null for the whole semester. */
  sectionId: number | null
  sectionCode: string | null
  /** The most enrolled students, or null for no limit. Used by course registration from v0.4. */
  capacity: number | null
  registrationStatus: RegistrationStatus
  status: LifecycleStatus
  /** Who teaches it, active first; null where the API does not load it. */
  faculty: OfferingFaculty[] | null
}

export type RegistrationStatus = 'DRAFT' | 'OPEN' | 'CLOSED'
export type TeachingRole = 'PRIMARY' | 'CO_FACULTY'

/** One faculty member's teaching assignment to an offering. */
export type OfferingFaculty = {
  teachingAssignmentId: number
  courseOfferingId: number
  facultyProfileId: number
  /** The employee ID. */
  loginId: string
  /** The name from the person profile, or null. */
  name: string | null
  departmentCode: string
  role: TeachingRole
  status: ActiveStatus
}

/** A faculty member to choose from: never contact details. */
export type FacultyLookup = {
  facultyProfileId: number
  loginId: string
  name: string | null
  departmentCode: string
  status: ActiveStatus
}

export type CourseOfferingFilters = { semesterId?: number; academicSessionId?: number }
export type CourseOfferingCreate = {
  courseId: number
  semesterId: number
  sectionId: number | null
  capacity: number | null
  registrationStatus: RegistrationStatus
}
export type CourseOfferingUpdate = { status: LifecycleStatus; capacity: number | null; registrationStatus: RegistrationStatus }

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
  /** The section the student is placed in, or null. */
  sectionId: number | null
  sectionCode: string | null
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

// The enrollment monitor: every course enrollment, filtered and paged on the server.
export type EnrollmentMonitorFilters = {
  academicSessionId?: number
  programId?: number
  semesterId?: number
  courseId?: number
  status?: EnrollmentStatus
  q?: string
  page: number
  size: number
}

export type EnrollmentMonitorRow = {
  courseEnrollmentId: number
  studentProfileId: number
  loginId: string
  name: string | null
  rollNumber: string | null
  courseCode: string
  courseName: string
  credits: number
  academicSessionCode: string
  programCode: string
  semesterNumber: number
  sectionCode: string | null
  status: EnrollmentStatus
  registeredAt: string
  droppedAt: string | null
}

export type EnrollmentMonitorPage = {
  items: EnrollmentMonitorRow[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
