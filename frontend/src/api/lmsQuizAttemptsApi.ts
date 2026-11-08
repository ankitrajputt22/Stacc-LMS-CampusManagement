import type { QuizAnswer, QuizAnswerInput, QuizAttempt, StudentQuiz } from '../types/lms'
import { apiClient } from './apiClient'

// The student is the one in the session; the backend decides from official enrollment what they may take.

/** The course's published quizzes, newest first, each with the student's own attempts. */
export async function getStudentQuizzes(lmsCourseId: number, signal?: AbortSignal): Promise<StudentQuiz[]> {
  return (await apiClient.get<StudentQuiz[]>(`/lms/courses/${lmsCourseId}/quizzes`, { signal })).data
}

export async function getStudentQuiz(lmsCourseId: number, quizId: number, signal?: AbortSignal): Promise<StudentQuiz> {
  return (await apiClient.get<StudentQuiz>(`/lms/courses/${lmsCourseId}/quizzes/${quizId}`, { signal })).data
}

/** Starts a new attempt, or returns the one still open. */
export async function startQuizAttempt(lmsCourseId: number, quizId: number): Promise<QuizAttempt> {
  return (await apiClient.post<QuizAttempt>(`/lms/courses/${lmsCourseId}/quizzes/${quizId}/attempts`)).data
}

export async function getQuizAttempt(attemptId: number, signal?: AbortSignal): Promise<QuizAttempt> {
  return (await apiClient.get<QuizAttempt>(`/lms/quiz-attempts/${attemptId}`, { signal })).data
}

/** Saves the answer to one question, replacing an earlier one, while the attempt is open. */
export async function saveQuizAnswer(attemptId: number, quizQuestionId: number, input: QuizAnswerInput): Promise<QuizAnswer> {
  return (await apiClient.put<QuizAnswer>(`/lms/quiz-attempts/${attemptId}/answers/${quizQuestionId}`, input)).data
}

/** Hands the attempt in. */
export async function submitQuizAttempt(attemptId: number): Promise<QuizAttempt> {
  return (await apiClient.post<QuizAttempt>(`/lms/quiz-attempts/${attemptId}/submit`)).data
}
