import type {
  AiAnswer,
  AiAssessmentDraft,
  AiAssessmentDraftInput,
  AiJob,
  AiKnowledgeStatus,
  AiKnowledgeSync,
  AiPracticeSet,
  AiStudentNote,
  AiTeacherDocument,
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
/** Queues a background refresh of the course's AI knowledge; answers at once with the queued job. */
export async function queueFacultyAiRefresh(lmsCourseId: number, signal?: AbortSignal): Promise<AiJob> {
  return (await apiClient.post<AiJob>(`/lms/faculty/courses/${lmsCourseId}/ai/jobs`, null, { signal })).data
}

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
export async function askCourseAi(
  lmsCourseId: number,
  question: string,
  signal?: AbortSignal,
  includeMyNotes = false,
): Promise<AiAnswer> {
  const body = includeMyNotes ? { question, includeMyNotes } : { question }
  return (await apiClient.post<AiAnswer>(`/lms/courses/${lmsCourseId}/ai/ask`, body, { signal })).data
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

/** The course's active teacher-only documents (v0.13.5). */
export async function getTeacherDocuments(lmsCourseId: number, signal?: AbortSignal): Promise<AiTeacherDocument[]> {
  const response = await apiClient.get<AiTeacherDocument[]>(`/lms/faculty/courses/${lmsCourseId}/ai/teacher-documents`, { signal })
  return response.data
}

export async function createTeacherDocument(lmsCourseId: number, title: string, content: string): Promise<AiTeacherDocument> {
  const response = await apiClient.post<AiTeacherDocument>(`/lms/faculty/courses/${lmsCourseId}/ai/teacher-documents`, { title, content })
  return response.data
}

export async function updateTeacherDocument(documentId: number, title: string, content: string): Promise<AiTeacherDocument> {
  const response = await apiClient.put<AiTeacherDocument>(`/lms/faculty/ai/teacher-documents/${documentId}`, { title, content })
  return response.data
}

export async function archiveTeacherDocument(documentId: number): Promise<AiTeacherDocument> {
  const response = await apiClient.post<AiTeacherDocument>(`/lms/faculty/ai/teacher-documents/${documentId}/archive`)
  return response.data
}

/** The signed-in student's own notes in the course (v0.13.6). */
export async function getMyNotes(lmsCourseId: number, signal?: AbortSignal): Promise<AiStudentNote[]> {
  return (await apiClient.get<AiStudentNote[]>(`/lms/courses/${lmsCourseId}/ai/notes`, { signal })).data
}

export async function createMyNote(lmsCourseId: number, title: string, content: string): Promise<AiStudentNote> {
  return (await apiClient.post<AiStudentNote>(`/lms/courses/${lmsCourseId}/ai/notes`, { title, content })).data
}

export async function updateMyNote(lmsCourseId: number, noteId: number, title: string, content: string): Promise<AiStudentNote> {
  return (await apiClient.put<AiStudentNote>(`/lms/courses/${lmsCourseId}/ai/notes/${noteId}`, { title, content })).data
}

/** Deletes the note for real; the backend removes it from the notes index too. */
export async function deleteMyNote(lmsCourseId: number, noteId: number): Promise<void> {
  await apiClient.delete(`/lms/courses/${lmsCourseId}/ai/notes/${noteId}`)
}
