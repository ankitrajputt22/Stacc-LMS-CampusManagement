import type { QuizAnswer, QuizAnswerInput, QuizAttempt, StudentQuiz } from '../types/lms'
import { apiClient } from './apiClient'

export async function getStudentQuizzes(lmsCourseId: number, signal?: AbortSignal): Promise<StudentQuiz[]> {
  return (await apiClient.get<StudentQuiz[]>(`/lms/courses/${lmsCourseId}/quizzes`, { signal })).data
}

export async function getStudentQuiz(lmsCourseId: number, quizId: number, signal?: AbortSignal): Promise<StudentQuiz> {
  return (await apiClient.get<StudentQuiz>(`/lms/courses/${lmsCourseId}/quizzes/${quizId}`, { signal })).data
}

export async function startQuizAttempt(lmsCourseId: number, quizId: number): Promise<QuizAttempt> {
  return (await apiClient.post<QuizAttempt>(`/lms/courses/${lmsCourseId}/quizzes/${quizId}/attempts`)).data
}

export async function getQuizAttempt(attemptId: number, signal?: AbortSignal): Promise<QuizAttempt> {
  return (await apiClient.get<QuizAttempt>(`/lms/quiz-attempts/${attemptId}`, { signal })).data
}

export async function saveQuizAnswer(attemptId: number, quizQuestionId: number, input: QuizAnswerInput): Promise<QuizAnswer> {
  return (await apiClient.put<QuizAnswer>(`/lms/quiz-attempts/${attemptId}/answers/${quizQuestionId}`, input)).data
}

export async function submitQuizAttempt(attemptId: number): Promise<QuizAttempt> {
  return (await apiClient.post<QuizAttempt>(`/lms/quiz-attempts/${attemptId}/submit`)).data
}
