import type {
  FacultyLookup,
  OfferingFaculty,
  TeachingRole,
  AdminCourseEnrollment,
  AdminCourseOffering,
  AdminSemesterEnrollment,
  CourseOfferingCreate,
  CourseOfferingFilters,
  CourseOfferingUpdate,
  EnrollmentMonitorFilters,
  EnrollmentMonitorPage,
  EnrollmentStatus,
  EnrollmentStudent,
  OfferingRoster,
  SemesterCourseEnrollments,
  StudentSemesterEnrollments,
} from '../types/admin'
import { apiClient } from './apiClient'

const OFFERINGS = '/admin/academic/offerings'
const ENROLLMENTS = '/admin/academic/enrollments'

export async function searchCourseEnrollments(filters: EnrollmentMonitorFilters, signal?: AbortSignal) {
  const response = await apiClient.get<EnrollmentMonitorPage>(`${ENROLLMENTS}/course-enrollments`, { params: filters, signal })
  return response.data
}

export async function getCourseOfferings(filters: CourseOfferingFilters, signal?: AbortSignal) {
  const response = await apiClient.get<AdminCourseOffering[]>(OFFERINGS, { params: filters, signal })
  return response.data
}

export async function getCourseOffering(courseOfferingId: number, signal?: AbortSignal) {
  const response = await apiClient.get<AdminCourseOffering>(`${OFFERINGS}/${courseOfferingId}`, { signal })
  return response.data
}

export async function createCourseOffering(body: CourseOfferingCreate) {
  const response = await apiClient.post<AdminCourseOffering>(OFFERINGS, body)
  return response.data
}

export async function updateCourseOffering(courseOfferingId: number, body: CourseOfferingUpdate) {
  const response = await apiClient.put<AdminCourseOffering>(`${OFFERINGS}/${courseOfferingId}`, body)
  return response.data
}

export async function getCourseOfferingEnrollments(courseOfferingId: number, signal?: AbortSignal) {
  const response = await apiClient.get<OfferingRoster>(`${OFFERINGS}/${courseOfferingId}/enrollments`, { signal })
  return response.data
}

export async function searchEnrollmentStudents(q: string, signal?: AbortSignal) {
  const response = await apiClient.get<EnrollmentStudent[]>(`${ENROLLMENTS}/students`, { params: { q }, signal })
  return response.data
}

export async function getStudentSemesterEnrollments(studentProfileId: number, signal?: AbortSignal) {
  const response = await apiClient.get<StudentSemesterEnrollments>(
    `${ENROLLMENTS}/students/${studentProfileId}/semesters`,
    { signal },
  )
  return response.data
}

export async function createSemesterEnrollment(studentProfileId: number, semesterId: number) {
  const response = await apiClient.post<AdminSemesterEnrollment>(`${ENROLLMENTS}/students/${studentProfileId}/semesters`, {
    semesterId,
  })
  return response.data
}

export async function updateSemesterEnrollmentStatus(semesterEnrollmentId: number, status: EnrollmentStatus) {
  const response = await apiClient.put<AdminSemesterEnrollment>(
    `${ENROLLMENTS}/semester-enrollments/${semesterEnrollmentId}/status`,
    { status },
  )
  return response.data
}

export async function changeSemesterEnrollmentSection(semesterEnrollmentId: number, sectionId: number | null) {
  const response = await apiClient.put<AdminSemesterEnrollment>(
    `${ENROLLMENTS}/semester-enrollments/${semesterEnrollmentId}/section`,
    { sectionId },
  )
  return response.data
}

export async function getSemesterCourseEnrollments(semesterEnrollmentId: number, signal?: AbortSignal) {
  const response = await apiClient.get<SemesterCourseEnrollments>(
    `${ENROLLMENTS}/semester-enrollments/${semesterEnrollmentId}/courses`,
    { signal },
  )
  return response.data
}

export async function createCourseEnrollment(semesterEnrollmentId: number, courseOfferingId: number) {
  const response = await apiClient.post<AdminCourseEnrollment>(
    `${ENROLLMENTS}/semester-enrollments/${semesterEnrollmentId}/courses`,
    { courseOfferingId },
  )
  return response.data
}

export async function updateCourseEnrollmentStatus(courseEnrollmentId: number, status: EnrollmentStatus) {
  const response = await apiClient.put<AdminCourseEnrollment>(
    `${ENROLLMENTS}/course-enrollments/${courseEnrollmentId}/status`,
    { status },
  )
  return response.data
}

export async function getOfferingFaculty(courseOfferingId: number, signal?: AbortSignal) {
  const response = await apiClient.get<OfferingFaculty[]>(`${OFFERINGS}/${courseOfferingId}/faculty`, { signal })
  return response.data
}

export async function searchFaculty(q: string, signal?: AbortSignal) {
  const response = await apiClient.get<FacultyLookup[]>('/admin/academic/faculty', { params: { q }, signal })
  return response.data
}

export async function assignFaculty(courseOfferingId: number, facultyProfileId: number, role: TeachingRole) {
  const response = await apiClient.post<OfferingFaculty>('/admin/academic/faculty-assignments', {
    courseOfferingId,
    facultyProfileId,
    role,
  })
  return response.data
}

export async function updateFacultyAssignment(teachingAssignmentId: number, role: TeachingRole, status: 'ACTIVE' | 'INACTIVE') {
  const response = await apiClient.put<OfferingFaculty>(`/admin/academic/faculty-assignments/${teachingAssignmentId}`, {
    role,
    status,
  })
  return response.data
}
