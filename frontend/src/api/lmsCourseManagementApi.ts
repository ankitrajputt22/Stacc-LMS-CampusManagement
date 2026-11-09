import type { LmsCourseManagement, LmsManagedCourse } from '../types/lms'
import { apiClient } from './apiClient'
import type { LmsCourseHomeView } from '../types/lms'

// The client's base URL already ends in /api. Every route needs the faculty role, FACULTY_COURSE_VIEW, and
// LMS_COURSE_MANAGE; which offerings a faculty member manages is decided on the backend from official teaching
// assignments, for the account in the access token. Nothing about the faculty member is sent from here.

/** Every course offering the faculty member teaches, with its LMS course when it has one, in course code order. */
export async function getManagedTeachingCourses(signal?: AbortSignal) {
  const response = await apiClient.get<LmsManagedCourse[]>('/lms/faculty/managed-courses', { signal })
  return response.data
}

/** Creates the draft LMS course of an offering. Its enrolled students use it once it is published. */
export async function createLmsCourse(courseOfferingId: number) {
  const response = await apiClient.post<LmsCourseManagement>(`/lms/faculty/course-offerings/${courseOfferingId}/lms-course`)
  return response.data
}

export async function publishLmsCourse(lmsCourseId: number) {
  const response = await apiClient.post<LmsCourseManagement>(`/lms/faculty/courses/${lmsCourseId}/publish`)
  return response.data
}

export async function archiveLmsCourse(lmsCourseId: number) {
  const response = await apiClient.post<LmsCourseManagement>(`/lms/faculty/courses/${lmsCourseId}/archive`)
  return response.data
}

/** Chooses what the course home shows and its welcome message. Official course details never change here. */
export async function changeCourseHome(
  lmsCourseId: number,
  input: { homeView: LmsCourseHomeView; welcomeMessage: string | null },
): Promise<{ homeView: LmsCourseHomeView; welcomeMessage: string | null }> {
  return (await apiClient.put<{ homeView: LmsCourseHomeView; welcomeMessage: string | null }>(
    `/lms/faculty/courses/${lmsCourseId}/home`,
    input,
  )).data
}


/** An earlier offering of the same course the faculty member taught, whose content can be copied. */
export type LmsCopySource = {
  lmsCourseId: number
  courseCode: string
  academicSessionCode: string
  semesterNumber: number
  programCode: string
  sectionCode: string | null
  lmsCourseStatus: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
}

export type LmsCopyResult = { modules: number; pages: number; files: number; syllabus: boolean }

/** The courses this course can copy content from, the latest term first. */
export async function getCopySources(lmsCourseId: number, signal?: AbortSignal): Promise<LmsCopySource[]> {
  return (await apiClient.get<LmsCopySource[]>(`/lms/faculty/courses/${lmsCourseId}/copy-sources`, { signal })).data
}

/** Copies modules, pages, files, and the syllabus text as drafts from an earlier offering. */
export async function copyCourseContent(lmsCourseId: number, sourceLmsCourseId: number): Promise<LmsCopyResult> {
  return (await apiClient.post<LmsCopyResult>(`/lms/faculty/courses/${lmsCourseId}/copy`, { sourceLmsCourseId })).data
}
