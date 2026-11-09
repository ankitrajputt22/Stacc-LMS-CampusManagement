import type {
  AiAnswer,
  AiAssessmentDraft,
  AiAssessmentDraftInput,
  AiKnowledgeStatus,
  AiKnowledgeSync,
  AiPracticeSet,
  AiTopicSummary,
} from '../types/ai'
import { apiClient } from './apiClient'

// AI learning requests. The browser only ever talks to Stacc's own API: the AI service is internal. Requests carry the
// question or generation settings only, never an account, student, or course content; the backend checks access and
// takes the account from the access token.

/** The course's AI knowledge status, for a course the signed-in faculty member teaches. */
export async function getFacultyAiStatus(lmsCourseId: number, signal?: AbortSignal): Promise<AiKnowledgeStatus> {
  return (await apiClient.get<AiKnowledgeStatus>(`/lms/faculty/courses/${lmsCourseId}/ai/status`, { signal })).data
}

/** Rebuilds the course's AI knowledge from its published content. */
export async function syncFacultyAiKnowledge(lmsCourseId: number, signal?: AbortSignal): Promise<AiKnowledgeSync> {
  return (await apiClient.post<AiKnowledgeSync>(`/lms/faculty/courses/${lmsCourseId}/ai/sync`, null, { signal })).data
}

/** An assessment draft for the teacher to review. It creates no assignment. */
export async function generateFacultyAssessmentDraft(
  lmsCourseId: number,
  input: AiAssessmentDraftInput,
  signal?: AbortSignal,
): Promise<AiAssessmentDraft> {
  return (
    await apiClient.post<AiAssessmentDraft>(`/lms/faculty/courses/${lmsCourseId}/ai/assessment-draft`, input, { signal })
  ).data
}

/** A grounded answer to the signed-in student's question about a course they may open. */
export async function askCourseAi(lmsCourseId: number, question: string, signal?: AbortSignal): Promise<AiAnswer> {
  return (await apiClient.post<AiAnswer>(`/lms/courses/${lmsCourseId}/ai/ask`, { question }, { signal })).data
}

/** A summary of one published topic of the course. */
export async function summarizeCourseTopic(
  lmsCourseId: number,
  topicId: number,
  signal?: AbortSignal,
): Promise<AiTopicSummary> {
  return (
    await apiClient.post<AiTopicSummary>(`/lms/courses/${lmsCourseId}/ai/topics/${topicId}/summary`, null, { signal })
  ).data
}

/** Practice questions for self-study. Nothing is saved or graded. */
export async function generateCoursePracticeQuestions(
  lmsCourseId: number,
  questionCount: number,
  topicId: number | null,
  signal?: AbortSignal,
): Promise<AiPracticeSet> {
  return (
    await apiClient.post<AiPracticeSet>(`/lms/courses/${lmsCourseId}/ai/practice`, { questionCount, topicId }, { signal })
  ).data
}
