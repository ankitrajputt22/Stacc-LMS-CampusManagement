import type {
  LmsCourseContent,
  LmsCourseProgress,
  LmsCourseFile,
  LmsFacultyCourseFiles,
  LmsCoursePage,
  LmsFacultyPage,
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

export async function getLmsCourseFiles(lmsCourseId: number, signal?: AbortSignal): Promise<LmsCourseFile[]> {
  const response = await apiClient.get<LmsCourseFile[]>(`/lms/courses/${lmsCourseId}/files`, { signal })
  return response.data
}

export async function getFacultyCourseFiles(lmsCourseId: number, signal?: AbortSignal): Promise<LmsFacultyCourseFiles> {
  const response = await apiClient.get<LmsFacultyCourseFiles>(`/lms/faculty/courses/${lmsCourseId}/files`, { signal })
  return response.data
}

export async function getCourseProgress(lmsCourseId: number, signal?: AbortSignal): Promise<LmsCourseProgress> {
  return (await apiClient.get<LmsCourseProgress>(`/lms/courses/${lmsCourseId}/progress`, { signal })).data
}

export async function completePage(lmsCourseId: number, pageId: number): Promise<LmsCourseProgress> {
  return (await apiClient.post<LmsCourseProgress>(`/lms/courses/${lmsCourseId}/pages/${pageId}/complete`)).data
}

export async function setPageRequired(pageId: number, required: boolean): Promise<LmsFacultyTopic> {
  return (await apiClient.put<LmsFacultyTopic>(`/lms/faculty/topics/${pageId}/required`, { required })).data
}

export async function setModuleRequiresPrevious(moduleId: number, required: boolean): Promise<LmsFacultyModule> {
  return (await apiClient.put<LmsFacultyModule>(`/lms/faculty/modules/${moduleId}/requires-previous`, { required })).data
}

export async function getLmsCoursePage(lmsCourseId: number, pageId: number, signal?: AbortSignal): Promise<LmsCoursePage> {
  const response = await apiClient.get<LmsCoursePage>(`/lms/courses/${lmsCourseId}/pages/${pageId}`, { signal })
  return response.data
}

export async function getFacultyPage(pageId: number, signal?: AbortSignal): Promise<LmsFacultyPage> {
  const response = await apiClient.get<LmsFacultyPage>(`/lms/faculty/pages/${pageId}`, { signal })
  return response.data
}

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

export async function archiveModule(moduleId: number): Promise<LmsFacultyModule> {
  const response = await apiClient.post<LmsFacultyModule>(
    `/lms/faculty/modules/${moduleId}/archive`,
  )
  return response.data
}

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

export async function archiveTopic(topicId: number): Promise<LmsFacultyTopic> {
  const response = await apiClient.post<LmsFacultyTopic>(
    `/lms/faculty/topics/${topicId}/archive`,
  )
  return response.data
}

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

export const FILE_TIMEOUT_MS = 5 * 60_000

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

export async function changeMaterialAiExclusion(materialId: number, excluded: boolean): Promise<LmsFacultyMaterial> {
  const response = await apiClient.put<LmsFacultyMaterial>(`/lms/faculty/materials/${materialId}/ai-exclusion`, { excluded })
  return response.data
}

export async function archiveMaterial(materialId: number): Promise<LmsFacultyMaterial> {
  const response = await apiClient.post<LmsFacultyMaterial>(
    `/lms/faculty/materials/${materialId}/archive`,
  )
  return response.data
}

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

export async function downloadMaterialFile(materialId: number): Promise<Blob> {
  const response = await apiClient.get<Blob>(`/lms/materials/${materialId}/download`, {
    responseType: 'blob',
    timeout: FILE_TIMEOUT_MS,
  })
  return response.data
}
