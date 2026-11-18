import type {
  LmsAnnouncementInput,
  LmsCourseAnnouncement,
  LmsFacultyAnnouncement,
} from '../types/lms'
import { apiClient } from './apiClient'
import { FILE_TIMEOUT_MS } from './lmsContentApi'

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

export async function archiveAnnouncement(announcementId: number): Promise<LmsFacultyAnnouncement> {
  const response = await apiClient.post<LmsFacultyAnnouncement>(
    `/lms/faculty/announcements/${announcementId}/archive`,
  )
  return response.data
}

export async function addAnnouncementFiles(announcementId: number, files: File[]): Promise<LmsFacultyAnnouncement> {
  const form = new FormData()
  files.forEach((file) => form.append('files', file))
  return (await apiClient.post<LmsFacultyAnnouncement>(`/lms/faculty/announcements/${announcementId}/files`, form, { timeout: FILE_TIMEOUT_MS })).data
}

export async function removeAnnouncementFile(announcementId: number, fileId: number): Promise<LmsFacultyAnnouncement> {
  return (await apiClient.post<LmsFacultyAnnouncement>(`/lms/faculty/announcements/${announcementId}/files/${fileId}/remove`)).data
}

export async function downloadFacultyAnnouncementFile(announcementId: number, fileId: number): Promise<Blob> {
  return (await apiClient.get<Blob>(`/lms/faculty/announcements/${announcementId}/files/${fileId}`, { responseType: 'blob', timeout: FILE_TIMEOUT_MS }))
    .data
}

export async function downloadCourseAnnouncementFile(lmsCourseId: number, announcementId: number, fileId: number): Promise<Blob> {
  return (
    await apiClient.get<Blob>(`/lms/courses/${lmsCourseId}/announcements/${announcementId}/files/${fileId}`, {
      responseType: 'blob',
      timeout: FILE_TIMEOUT_MS,
    })
  ).data
}
