import type {
  LmsAnnouncementInput,
  LmsCourseAnnouncement,
  LmsFacultyAnnouncement,
} from '../types/lms'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api. The signed-in account is the one in the access token;
// no account, student, or faculty ID is ever sent from here.

/** The published announcements of a course the signed-in student may use, newest published first. */
export async function getCourseAnnouncements(
  lmsCourseId: number,
  signal?: AbortSignal,
): Promise<LmsCourseAnnouncement[]> {
  const response = await apiClient.get<LmsCourseAnnouncement[]>(
    `/lms/courses/${lmsCourseId}/announcements`,
    { signal },
  )
  return response.data
}

/** The draft and published announcements of a course the signed-in faculty member teaches, newest created first. */
export async function getFacultyAnnouncements(
  lmsCourseId: number,
  signal?: AbortSignal,
): Promise<LmsFacultyAnnouncement[]> {
  const response = await apiClient.get<LmsFacultyAnnouncement[]>(
    `/lms/faculty/courses/${lmsCourseId}/announcements`,
    { signal },
  )
  return response.data
}

/** Adds a draft announcement to the course. */
export async function createAnnouncement(
  lmsCourseId: number,
  input: LmsAnnouncementInput,
): Promise<LmsFacultyAnnouncement> {
  const response = await apiClient.post<LmsFacultyAnnouncement>(
    `/lms/faculty/courses/${lmsCourseId}/announcements`,
    input,
  )
  return response.data
}

/** Changes the title and message. A published announcement stays published and keeps its date. */
export async function updateAnnouncement(
  announcementId: number,
  input: LmsAnnouncementInput,
): Promise<LmsFacultyAnnouncement> {
  const response = await apiClient.put<LmsFacultyAnnouncement>(
    `/lms/faculty/announcements/${announcementId}`,
    input,
  )
  return response.data
}

export async function publishAnnouncement(announcementId: number): Promise<LmsFacultyAnnouncement> {
  const response = await apiClient.post<LmsFacultyAnnouncement>(
    `/lms/faculty/announcements/${announcementId}/publish`,
  )
  return response.data
}

/** Archives the announcement. It is kept, but students and the faculty list no longer show it. */
export async function archiveAnnouncement(announcementId: number): Promise<LmsFacultyAnnouncement> {
  const response = await apiClient.post<LmsFacultyAnnouncement>(
    `/lms/faculty/announcements/${announcementId}/archive`,
  )
  return response.data
}
