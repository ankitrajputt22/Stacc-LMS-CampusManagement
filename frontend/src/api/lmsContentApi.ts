import type {
  LmsCourseContent,
  LmsFacultyCourseContent,
  LmsFacultyMaterial,
  LmsFacultyModule,
  LmsFacultyTopic,
  LmsMaterialInput,
  LmsMaterialUpload,
  LmsModuleInput,
  LmsTopicInput,
} from '../types/lms'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api, so every path here starts below it. The signed-in
// account is the one in the access token; no account, faculty, or student ID is ever sent from here.

/** The published content of a course the signed-in student may use. Access is checked again by the backend. */
export async function getLmsCourseContent(
  lmsCourseId: number,
  signal?: AbortSignal,
): Promise<LmsCourseContent> {
  const response = await apiClient.get<LmsCourseContent>(
    `/lms/courses/${lmsCourseId}/content`,
    { signal },
  )
  return response.data
}

/** A course the signed-in faculty member teaches, with every module and topic that is not archived. */
export async function getFacultyCourseContent(
  lmsCourseId: number,
  signal?: AbortSignal,
): Promise<LmsFacultyCourseContent> {
  const response = await apiClient.get<LmsFacultyCourseContent>(
    `/lms/faculty/courses/${lmsCourseId}/content`,
    { signal },
  )
  return response.data
}

/** Adds a draft module after the course's current modules. */
export async function createModule(
  lmsCourseId: number,
  input: LmsModuleInput,
): Promise<LmsFacultyModule> {
  const response = await apiClient.post<LmsFacultyModule>(
    `/lms/faculty/courses/${lmsCourseId}/modules`,
    input,
  )
  return response.data
}

export async function updateModule(
  moduleId: number,
  input: LmsModuleInput,
): Promise<LmsFacultyModule> {
  const response = await apiClient.put<LmsFacultyModule>(
    `/lms/faculty/modules/${moduleId}`,
    input,
  )
  return response.data
}

export async function publishModule(moduleId: number): Promise<LmsFacultyModule> {
  const response = await apiClient.post<LmsFacultyModule>(
    `/lms/faculty/modules/${moduleId}/publish`,
  )
  return response.data
}

/** Archives the module. Nothing is deleted, and it no longer appears in the content list. */
export async function archiveModule(moduleId: number): Promise<LmsFacultyModule> {
  const response = await apiClient.post<LmsFacultyModule>(
    `/lms/faculty/modules/${moduleId}/archive`,
  )
  return response.data
}

/** Puts the course's current modules in this order. The list must hold each of them exactly once. */
export async function reorderModules(
  lmsCourseId: number,
  moduleIds: number[],
): Promise<LmsFacultyModule[]> {
  const response = await apiClient.put<LmsFacultyModule[]>(
    `/lms/faculty/courses/${lmsCourseId}/modules/reorder`,
    { moduleIds },
  )
  return response.data
}

/** Adds a draft topic after the module's current topics. */
export async function createTopic(
  moduleId: number,
  input: LmsTopicInput,
): Promise<LmsFacultyTopic> {
  const response = await apiClient.post<LmsFacultyTopic>(
    `/lms/faculty/modules/${moduleId}/topics`,
    input,
  )
  return response.data
}

export async function updateTopic(
  topicId: number,
  input: LmsTopicInput,
): Promise<LmsFacultyTopic> {
  const response = await apiClient.put<LmsFacultyTopic>(
    `/lms/faculty/topics/${topicId}`,
    input,
  )
  return response.data
}

export async function publishTopic(topicId: number): Promise<LmsFacultyTopic> {
  const response = await apiClient.post<LmsFacultyTopic>(
    `/lms/faculty/topics/${topicId}/publish`,
  )
  return response.data
}

/** Archives the topic. Nothing is deleted, and it no longer appears in the content list. */
export async function archiveTopic(topicId: number): Promise<LmsFacultyTopic> {
  const response = await apiClient.post<LmsFacultyTopic>(
    `/lms/faculty/topics/${topicId}/archive`,
  )
  return response.data
}

/** Puts the module's current topics in this order. The list must hold each of them exactly once. */
export async function reorderTopics(
  moduleId: number,
  topicIds: number[],
): Promise<LmsFacultyTopic[]> {
  const response = await apiClient.put<LmsFacultyTopic[]>(
    `/lms/faculty/modules/${moduleId}/topics/reorder`,
    { topicIds },
  )
  return response.data
}

// Files can take longer than ordinary requests, so uploads and downloads get more time.
export const FILE_TIMEOUT_MS = 5 * 60_000

/**
 * Uploads a study material as multipart/form-data. The browser sets the multipart header itself; no
 * Content-Type is set here. The file is stored privately and the material starts as a draft.
 */
export async function uploadMaterial(
  topicId: number,
  upload: LmsMaterialUpload,
): Promise<LmsFacultyMaterial> {
  const form = new FormData()
  form.append('title', upload.title)
  if (upload.description) {
    form.append('description', upload.description)
  }
  form.append('file', upload.file)
  const response = await apiClient.post<LmsFacultyMaterial>(
    `/lms/faculty/topics/${topicId}/materials`,
    form,
    { timeout: FILE_TIMEOUT_MS },
  )
  return response.data
}

export async function updateMaterial(
  materialId: number,
  input: LmsMaterialInput,
): Promise<LmsFacultyMaterial> {
  const response = await apiClient.put<LmsFacultyMaterial>(
    `/lms/faculty/materials/${materialId}`,
    input,
  )
  return response.data
}

export async function publishMaterial(materialId: number): Promise<LmsFacultyMaterial> {
  const response = await apiClient.post<LmsFacultyMaterial>(
    `/lms/faculty/materials/${materialId}/publish`,
  )
  return response.data
}

/** Archives the material. Its record and stored file are kept, and it leaves the content list. */
export async function archiveMaterial(materialId: number): Promise<LmsFacultyMaterial> {
  const response = await apiClient.post<LmsFacultyMaterial>(
    `/lms/faculty/materials/${materialId}/archive`,
  )
  return response.data
}

/** Puts the topic's current materials in this order. The list must hold each of them exactly once. */
export async function reorderMaterials(
  topicId: number,
  materialIds: number[],
): Promise<LmsFacultyMaterial[]> {
  const response = await apiClient.put<LmsFacultyMaterial[]>(
    `/lms/faculty/topics/${topicId}/materials/reorder`,
    { materialIds },
  )
  return response.data
}

/**
 * Downloads a study material's file through the API, with the signed-in user's token. The backend
 * decides whether this user may have it; storage is never contacted from the browser.
 */
export async function downloadMaterialFile(materialId: number): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/lms/materials/${materialId}/download`, {
    responseType: 'blob',
    timeout: FILE_TIMEOUT_MS,
  })
  return response.data
}
