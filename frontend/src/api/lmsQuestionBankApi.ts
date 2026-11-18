import type { LmsQuestion, LmsQuestionInput } from '../types/lms'
import { apiClient } from './apiClient'

export async function getQuestions(lmsCourseId: number, signal?: AbortSignal): Promise<LmsQuestion[]> {
  return (await apiClient.get<LmsQuestion[]>(`/lms/faculty/courses/${lmsCourseId}/questions`, { signal })).data
}

export async function addQuestion(lmsCourseId: number, input: LmsQuestionInput): Promise<LmsQuestion> {
  return (await apiClient.post<LmsQuestion>(`/lms/faculty/courses/${lmsCourseId}/questions`, input)).data
}

export async function editQuestion(questionId: number, input: LmsQuestionInput): Promise<LmsQuestion> {
  return (await apiClient.put<LmsQuestion>(`/lms/faculty/questions/${questionId}`, input)).data
}

export async function markQuestionReviewed(questionId: number): Promise<LmsQuestion> {
  return (await apiClient.post<LmsQuestion>(`/lms/faculty/questions/${questionId}/review`)).data
}

export async function allowQuestionPractice(questionId: number, allowed: boolean): Promise<LmsQuestion> {
  return (await apiClient.put<LmsQuestion>(`/lms/faculty/questions/${questionId}/practice`, { allowed })).data
}

export async function archiveQuestion(questionId: number): Promise<LmsQuestion> {
  return (await apiClient.post<LmsQuestion>(`/lms/faculty/questions/${questionId}/archive`)).data
}
