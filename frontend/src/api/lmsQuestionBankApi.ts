import type { LmsQuestion, LmsQuestionInput } from '../types/lms'
import { apiClient } from './apiClient'

// The signed-in account is the one in the session; the backend checks on every request that it teaches the course.

/** Every question of the course's bank with its answers, archived ones included, newest first. */
export async function getQuestions(lmsCourseId: number, signal?: AbortSignal): Promise<LmsQuestion[]> {
  return (await apiClient.get<LmsQuestion[]>(`/lms/faculty/courses/${lmsCourseId}/questions`, { signal })).data
}

/** Adds a question; it starts needing review and is not offered for practice. */
export async function addQuestion(lmsCourseId: number, input: LmsQuestionInput): Promise<LmsQuestion> {
  return (await apiClient.post<LmsQuestion>(`/lms/faculty/courses/${lmsCourseId}/questions`, input)).data
}

/** Replaces the question; it then needs a new review and is no longer offered for practice. */
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
