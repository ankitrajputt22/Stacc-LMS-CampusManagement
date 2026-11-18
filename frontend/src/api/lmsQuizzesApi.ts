import type { LmsQuiz, LmsQuizSettingsInput, LmsQuizSummary, QuizAttemptReview, QuizResults } from '../types/lms'
import { apiClient } from './apiClient'

export async function getFacultyQuizzes(lmsCourseId: number, signal?: AbortSignal): Promise<LmsQuizSummary[]> {
  return (await apiClient.get<LmsQuizSummary[]>(`/lms/faculty/courses/${lmsCourseId}/quizzes`, { signal })).data
}

export async function createQuiz(lmsCourseId: number, input: LmsQuizSettingsInput): Promise<LmsQuiz> {
  return (await apiClient.post<LmsQuiz>(`/lms/faculty/courses/${lmsCourseId}/quizzes`, input)).data
}

export async function getFacultyQuiz(quizId: number, signal?: AbortSignal): Promise<LmsQuiz> {
  return (await apiClient.get<LmsQuiz>(`/lms/faculty/quizzes/${quizId}`, { signal })).data
}

export async function updateQuiz(quizId: number, input: LmsQuizSettingsInput): Promise<LmsQuiz> {
  return (await apiClient.put<LmsQuiz>(`/lms/faculty/quizzes/${quizId}`, input)).data
}

export async function addQuizQuestion(quizId: number, questionId: number, marks?: number): Promise<LmsQuiz> {
  return (await apiClient.post<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/questions`, { questionId, marks })).data
}

export async function changeQuizQuestionMarks(quizId: number, quizQuestionId: number, marks: number): Promise<LmsQuiz> {
  return (await apiClient.put<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/questions/${quizQuestionId}`, { marks })).data
}

export async function removeQuizQuestion(quizId: number, quizQuestionId: number): Promise<LmsQuiz> {
  return (await apiClient.delete<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/questions/${quizQuestionId}`)).data
}

export async function reorderQuizQuestions(quizId: number, quizQuestionIds: number[]): Promise<LmsQuiz> {
  return (await apiClient.put<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/question-order`, { quizQuestionIds })).data
}

export async function publishQuiz(quizId: number): Promise<LmsQuiz> {
  return (await apiClient.post<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/publish`)).data
}

export async function archiveQuiz(quizId: number): Promise<LmsQuiz> {
  return (await apiClient.post<LmsQuiz>(`/lms/faculty/quizzes/${quizId}/archive`)).data
}

export async function getQuizResults(quizId: number, signal?: AbortSignal): Promise<QuizResults> {
  return (await apiClient.get<QuizResults>(`/lms/faculty/quizzes/${quizId}/attempts`, { signal })).data
}

export async function getQuizAttemptReview(attemptId: number, signal?: AbortSignal): Promise<QuizAttemptReview> {
  return (await apiClient.get<QuizAttemptReview>(`/lms/faculty/quiz-attempts/${attemptId}`, { signal })).data
}

export async function markQuizAnswer(
  attemptId: number,
  quizQuestionId: number,
  marks: number,
  feedback: string | null,
): Promise<QuizAttemptReview> {
  return (
    await apiClient.put<QuizAttemptReview>(`/lms/faculty/quiz-attempts/${attemptId}/answers/${quizQuestionId}/mark`, { marks, feedback })
  ).data
}

export async function closeQuizAttempt(attemptId: number): Promise<QuizAttemptReview> {
  return (await apiClient.post<QuizAttemptReview>(`/lms/faculty/quiz-attempts/${attemptId}/close`)).data
}
