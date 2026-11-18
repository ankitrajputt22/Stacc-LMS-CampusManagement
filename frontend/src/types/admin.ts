export type ActiveStatus = 'ACTIVE' | 'INACTIVE'
export type DegreeType = 'UNDERGRADUATE' | 'POSTGRADUATE' | 'DIPLOMA' | 'DOCTORATE' | 'CERTIFICATE'
export type CourseType = 'CORE' | 'ELECTIVE' | 'LAB' | 'PROJECT' | 'AUDIT'
export type LifecycleStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED'

export type AdminDepartment = {
  id: number
  code: string
  name: string
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
  maxCreditsPerSemester: number | null
}

export type AdminAcademicSession = {
  id: number
  code: string
  startDate: string
  endDate: string
  status: LifecycleStatus
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
  credits: number
  courseType: CourseType
  status: ActiveStatus
}

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
  capacity: number | null
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
export type SemesterRegistrationUpdate = {
  registrationOpensAt: string | null
  registrationClosesAt: string | null
  dropDeadline: string | null
}
type CourseDetails = { shortName: string | null; description: string | null; courseType: CourseType }
export type CourseCreate = { departmentId: number; code: string; name: string; credits: number } & CourseDetails
export type CourseUpdate = { name: string; credits: number; status: ActiveStatus } & CourseDetails

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
  sectionId: number | null
  sectionCode: string | null
  capacity: number | null
  registrationStatus: RegistrationStatus
  status: LifecycleStatus
  faculty: OfferingFaculty[] | null
}

export type RegistrationStatus = 'DRAFT' | 'OPEN' | 'CLOSED'
export type TeachingRole = 'PRIMARY' | 'CO_FACULTY'

export type OfferingFaculty = {
  teachingAssignmentId: number
  courseOfferingId: number
  facultyProfileId: number
  loginId: string
  name: string | null
  departmentCode: string
  role: TeachingRole
  status: ActiveStatus
}

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
  sectionId: number | null
  sectionCode: string | null
  status: EnrollmentStatus
}

export type StudentSemesterEnrollments = {
  student: EnrollmentStudent
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
  courseEnrollments: AdminCourseEnrollment[]
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
  enrollments: RosterEntry[]
}

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
