import type { ActiveStatus, CourseType, DegreeType, EnrollmentStatus, TeachingRole } from './admin'

export type CurrentPlacement = {
  academicSessionCode: string
  semesterNumber: number
  sectionCode: string | null
}

export type StudentProfileSummary = {
  studentProfileId: number
  loginId: string
  name: string | null
  rollNumber: string | null
  programId: number
  programCode: string
  programName: string
  departmentCode: string
  admissionSessionCode: string
  status: ActiveStatus
  current: CurrentPlacement | null
}

export type FacultyProfileSummary = {
  facultyProfileId: number
  loginId: string
  name: string | null
  departmentId: number
  departmentCode: string
  departmentName: string
  status: ActiveStatus
  activeTeachingCount: number
}

export type MyCourseRecord = {
  courseCode: string
  courseName: string
  credits: number
  courseType: CourseType
  status: EnrollmentStatus
  result: { marksObtained: number; maxMarks: number; gradeCode: string | null; publishedAt: string } | null
  teachers: string[]
}

export type MySemesterRecord = {
  academicSessionCode: string
  semesterNumber: number
  programCode: string
  sectionCode: string | null
  status: EnrollmentStatus
  courses: MyCourseRecord[]
}

export type MyAcademicProfile = {
  student: {
    loginId: string
    rollNumber: string | null
    programCode: string
    programName: string
    degreeType: DegreeType
    departmentCode: string
    departmentName: string
    admissionSessionCode: string
    status: ActiveStatus
    current: CurrentPlacement | null
    semesters: MySemesterRecord[]
    headOfDepartment: string | null
  } | null
  faculty: {
    loginId: string
    departmentCode: string
    departmentName: string
    status: ActiveStatus
    teaching: {
      courseOfferingId: number
      courseCode: string
      courseName: string
      programCode: string
      semesterNumber: number
      academicSessionCode: string
      sectionCode: string | null
      role: TeachingRole
    }[]
    headOfDepartment: string | null
  } | null
}
