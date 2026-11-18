import type {
  ExamBlueprint,
  ExamBlueprintRowInput,
  ExamPaper,
  ExamPaperDetailsInput,
  ExamPaperQuestionInput,
  ExamPaperSummary,
} from '../types/lms'
import { apiClient } from './apiClient'
import { FILE_TIMEOUT_MS } from './lmsContentApi'

export async function getExamBlueprints(lmsCourseId: number, signal?: AbortSignal): Promise<ExamBlueprint[]> {
  return (await apiClient.get<ExamBlueprint[]>(`/lms/faculty/courses/${lmsCourseId}/exam-blueprints`, { signal })).data
}

export async function addExamBlueprint(lmsCourseId: number, title: string, rows: ExamBlueprintRowInput[]): Promise<ExamBlueprint> {
  return (await apiClient.post<ExamBlueprint>(`/lms/faculty/courses/${lmsCourseId}/exam-blueprints`, { title, rows })).data
}

export async function editExamBlueprint(blueprintId: number, title: string, rows: ExamBlueprintRowInput[]): Promise<ExamBlueprint> {
  return (await apiClient.put<ExamBlueprint>(`/lms/faculty/exam-blueprints/${blueprintId}`, { title, rows })).data
}

export async function archiveExamBlueprint(blueprintId: number): Promise<ExamBlueprint> {
  return (await apiClient.post<ExamBlueprint>(`/lms/faculty/exam-blueprints/${blueprintId}/archive`)).data
}

export async function buildExamPaper(blueprintId: number, input: ExamPaperDetailsInput): Promise<ExamPaper> {
  return (await apiClient.post<ExamPaper>(`/lms/faculty/exam-blueprints/${blueprintId}/papers`, input)).data
}

export async function getExamPapers(lmsCourseId: number, signal?: AbortSignal): Promise<ExamPaperSummary[]> {
  return (await apiClient.get<ExamPaperSummary[]>(`/lms/faculty/courses/${lmsCourseId}/exam-papers`, { signal })).data
}

export async function addExamPaper(lmsCourseId: number, input: ExamPaperDetailsInput): Promise<ExamPaper> {
  return (await apiClient.post<ExamPaper>(`/lms/faculty/courses/${lmsCourseId}/exam-papers`, input)).data
}

export async function getExamPaper(paperId: number, signal?: AbortSignal): Promise<ExamPaper> {
  return (await apiClient.get<ExamPaper>(`/lms/faculty/exam-papers/${paperId}`, { signal })).data
}

export async function editExamPaper(paperId: number, input: ExamPaperDetailsInput): Promise<ExamPaper> {
  return (await apiClient.put<ExamPaper>(`/lms/faculty/exam-papers/${paperId}`, input)).data
}

export async function setExamPaperQuestions(paperId: number, questions: ExamPaperQuestionInput[]): Promise<ExamPaper> {
  return (await apiClient.put<ExamPaper>(`/lms/faculty/exam-papers/${paperId}/questions`, { questions })).data
}

export async function finalizeExamPaper(paperId: number): Promise<ExamPaper> {
  return (await apiClient.post<ExamPaper>(`/lms/faculty/exam-papers/${paperId}/finalize`)).data
}

export async function archiveExamPaper(paperId: number): Promise<ExamPaper> {
  return (await apiClient.post<ExamPaper>(`/lms/faculty/exam-papers/${paperId}/archive`)).data
}

export async function downloadExamPaper(paperId: number, answers: boolean): Promise<Blob> {
  return (
    await apiClient.get<Blob>(`/lms/faculty/exam-papers/${paperId}/document`, {
      params: { answers },
      responseType: 'blob',
      timeout: FILE_TIMEOUT_MS,
    })
  ).data
}

export async function buildExamPaperSets(
  blueprintId: number,
  input: { title: string | null; sets: number; distinct: boolean },
): Promise<ExamPaper[]> {
  return (await apiClient.post<ExamPaper[]>(`/lms/faculty/exam-blueprints/${blueprintId}/paper-sets`, input)).data
}

export type BlueprintRowShortfall = { position: number; sectionTitle: string | null; needed: number; available: number; missing: number }

export async function getBlueprintShortfall(blueprintId: number, signal?: AbortSignal): Promise<BlueprintRowShortfall[]> {
  return (await apiClient.get<BlueprintRowShortfall[]>(`/lms/faculty/exam-blueprints/${blueprintId}/shortfall`, { signal })).data
}

export type BlueprintFillResult = {
  rows: { position: number; sectionTitle: string | null; missing: number; added: number; droppedForKeyMismatch: number }[]
  note: string | null
}

export async function fillBlueprintShortfall(blueprintId: number): Promise<BlueprintFillResult> {
  return (await apiClient.post<BlueprintFillResult>(`/lms/faculty/ai/exam-blueprints/${blueprintId}/fill-shortfall`)).data
}
