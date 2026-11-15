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

// The signed-in account is the one in the session; the backend checks on every request that it teaches the course.

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

/** Builds a draft paper from the blueprint with questions chosen at random from the bank. */
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

/** Replaces every question of a draft paper with this list, in its order. */
export async function setExamPaperQuestions(paperId: number, questions: ExamPaperQuestionInput[]): Promise<ExamPaper> {
  return (await apiClient.put<ExamPaper>(`/lms/faculty/exam-papers/${paperId}/questions`, { questions })).data
}

export async function finalizeExamPaper(paperId: number): Promise<ExamPaper> {
  return (await apiClient.post<ExamPaper>(`/lms/faculty/exam-papers/${paperId}/finalize`)).data
}

export async function archiveExamPaper(paperId: number): Promise<ExamPaper> {
  return (await apiClient.post<ExamPaper>(`/lms/faculty/exam-papers/${paperId}/archive`)).data
}

/** The paper as a Word document, with the answer key on its own page when asked. */
export async function downloadExamPaper(paperId: number, answers: boolean): Promise<Blob> {
  return (
    await apiClient.get<Blob>(`/lms/faculty/exam-papers/${paperId}/document`, {
      params: { answers },
      responseType: 'blob',
      timeout: FILE_TIMEOUT_MS,
    })
  ).data
}

/** Builds two to four sets of a paper from the blueprint; with distinct, no question is in two sets (v0.14.4). */
export async function buildExamPaperSets(
  blueprintId: number,
  input: { title: string | null; sets: number; distinct: boolean },
): Promise<ExamPaper[]> {
  return (await apiClient.post<ExamPaper[]>(`/lms/faculty/exam-blueprints/${blueprintId}/paper-sets`, input)).data
}

/** For each blueprint row, how many reviewed questions it needs and how many the bank has (v0.14.4). */
export type BlueprintRowShortfall = { position: number; sectionTitle: string | null; needed: number; available: number; missing: number }

export async function getBlueprintShortfall(blueprintId: number, signal?: AbortSignal): Promise<BlueprintRowShortfall[]> {
  return (await apiClient.get<BlueprintRowShortfall[]>(`/lms/faculty/exam-blueprints/${blueprintId}/shortfall`, { signal })).data
}

/** What AI drafted into the bank for each short row, as Needs review (v0.14.4). */
export type BlueprintFillResult = {
  rows: { position: number; sectionTitle: string | null; missing: number; added: number; droppedForKeyMismatch: number }[]
  note: string | null
}

export async function fillBlueprintShortfall(blueprintId: number): Promise<BlueprintFillResult> {
  return (await apiClient.post<BlueprintFillResult>(`/lms/faculty/ai/exam-blueprints/${blueprintId}/fill-shortfall`)).data
}

