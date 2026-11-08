import type { LmsQuiz, LmsQuizSettingsInput, LmsQuizSummary } from '../types/lms'
import { apiClient } from './apiClient'

// The signed-in account is the one in the session; the backend checks on every request that it teaches the course.

/** Every quiz of the course, archived ones included, newest first. */
export async function getFacultyQuizzes(lmsCourseId: number, signal?: AbortSignal): Promise<LmsQuizSummary[]> {
  return (await apiClient.get<LmsQuizSummary[]>(`/lms/faculty/courses/${lmsCourseId}/quizzes`, { signal })).data
}

/** Adds a draft quiz without questions. */
export async function createQuiz(lmsCourseId: number, input: LmsQuizSettingsInput): Promise<LmsQuiz> {
  return (await apiClient.post<LmsQuiz>(`/lms/faculty/courses/${lmsCourseId}/quizzes`, input)).data
}

export async function getFacultyQuiz(quizId: number, signal?: AbortSignal): Promise<LmsQuiz> {
  return (await apiClient.get<LmsQuiz>(`/lms/faculty/quizzes/${quizId}`, { signal })).data
}

/** Changes the settings; after publishing the type, shuffling, and negative marking stay as they are. */
export async function updateQuiz(quizId: number, input: LmsQuizSettingsInput): Promise<LmsQuiz> {
  return (await apiClient.put<LmsQuiz>(`/lms/faculty/quizzes/${quizId}`, input)).data
}

/** Adds a bank question at the end of a draft quiz, with the bank's marks unless others are given. */
export async function addQuizQuestion(quizId: number, questionId: number, marks?: number): Promise<LmsQuiz> {
  return (await apiClient.post<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/questions`, { questionId, marks })).data
}

export async function changeQuizQuestionMarks(quizId: number, quizQuestionId: number, marks: number): Promise<LmsQuiz> {
  return (await apiClient.put<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/questions/${quizQuestionId}`, { marks })).data
}

/** Takes a question out of a draft quiz; it stays in the bank. */
export async function removeQuizQuestion(quizId: number, quizQuestionId: number): Promise<LmsQuiz> {
  return (await apiClient.delete<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/questions/${quizQuestionId}`)).data
}

/** Puts the questions of a draft quiz in a new order, naming every question once. */
export async function reorderQuizQuestions(quizId: number, quizQuestionIds: number[]): Promise<LmsQuiz> {
  return (await apiClient.put<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/question-order`, { quizQuestionIds })).data
}

export async function publishQuiz(quizId: number): Promise<LmsQuiz> {
  return (await apiClient.post<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/publish`)).data
}

export async function archiveQuiz(quizId: number): Promise<LmsQuiz> {
  return (await apiClient.post<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/archive`)).data
}
