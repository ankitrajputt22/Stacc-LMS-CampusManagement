import type {
  AiAnswer,
  AiAnswerStyle,
  AiAskMode,
  AiAssessmentDraft,
  AiAssessmentDraftInput,
  AiCitation,
  AiFlashcard,
  AiFlashcardSet,
  AiFlashcardSetSummary,
  AiFollowUpTurn,
  AiJob,
  AiKnowledgeStatus,
  AiKnowledgeSync,
  AiModelAnswer,
  AiPracticeSet,
  AiQuestionBankDraft,
  AiQuizDraft,
  AiRubricSuggestion,
  AiSavedAnswer,
  AiStudentNote,
  AiSummary,
  AiSummaryScope,
  AiSummaryStyle,
  AiTeacherDocument,
  AiTopicSummary,
  ReviewRating,
} from '../types/ai'
import type { QuestionDifficulty, QuestionType } from '../types/lms'
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
  options: { mode?: AiAskMode; moduleId?: number | null; history?: AiFollowUpTurn[]; style?: AiAnswerStyle } = {},
): Promise<AiAnswer> {
  const body = {
    question,
    mode: options.mode ?? 'COURSE',
    ...(options.moduleId ? { moduleId: options.moduleId } : {}),
    ...(options.history && options.history.length > 0 ? { history: options.history } : {}),
    ...(options.style && options.style !== 'NORMAL' ? { style: options.style } : {}),
  }
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

/** Turns the course's student AI Study Assistant on or off (v0.13.7). */
export async function changeStudentAiAccess(lmsCourseId: number, enabled: boolean): Promise<{ enabled: boolean }> {
  return (await apiClient.put<{ enabled: boolean }>(`/lms/faculty/courses/${lmsCourseId}/ai/student-access`, { enabled })).data
}

/** Drafts questions into the course's question bank as Needs review (v0.14.1). */
export async function draftIntoQuestionBank(
  lmsCourseId: number,
  input: { questionCount: number; questionTypes: QuestionType[]; difficulty: QuestionDifficulty; topicId: number | null },
  signal?: AbortSignal,
): Promise<AiQuestionBankDraft> {
  const body = { ...input, ...(input.topicId === null ? { topicId: undefined } : {}) }
  return (await apiClient.post<AiQuestionBankDraft>(`/lms/faculty/courses/${lmsCourseId}/ai/question-bank`, body, { signal })).data
}

/** Builds an ordinary draft quiz from reviewed bank questions, with AI only for gaps when allowed (v0.14.3). */
export async function draftQuiz(
  lmsCourseId: number,
  input: {
    title: string
    type: 'GRADED' | 'PRACTICE'
    questionCount: number
    questionTypes: QuestionType[]
    difficulty: QuestionDifficulty | null
    fillGapsWithAi: boolean
  },
  signal?: AbortSignal,
): Promise<AiQuizDraft> {
  return (await apiClient.post<AiQuizDraft>(`/lms/faculty/courses/${lmsCourseId}/ai/quiz-draft`, input, { signal })).data
}

/** Suggests a model answer to a bank question, with sources (v0.14.5). */
export async function suggestModelAnswer(questionId: number, signal?: AbortSignal): Promise<AiModelAnswer> {
  return (await apiClient.post<AiModelAnswer>(`/lms/faculty/ai/questions/${questionId}/model-answer`, undefined, { signal })).data
}

/** Suggests a rubric for an assignment whose points add up to its maximum marks (v0.14.5). */
export async function suggestRubric(assignmentId: number, criteriaCount: number, signal?: AbortSignal): Promise<AiRubricSuggestion> {
  return (await apiClient.post<AiRubricSuggestion>(`/lms/faculty/ai/assignments/${assignmentId}/rubric-suggestion`, { criteriaCount },
    { signal })).data
}

/** The signed-in student's saved answers in the course, newest first (v0.15.1). */
export async function getSavedAnswers(lmsCourseId: number, signal?: AbortSignal): Promise<AiSavedAnswer[]> {
  return (await apiClient.get<AiSavedAnswer[]>(`/lms/courses/${lmsCourseId}/ai/saved-answers`, { signal })).data
}

/** Saves an answer; the backend rebuilds its sources from what the student may use now. */
export async function saveAnswer(lmsCourseId: number, question: string, answer: string, citations: AiCitation[]): Promise<AiSavedAnswer> {
  const sources = citations.map((citation) => ({
    sourceType: citation.sourceType, sourceId: citation.sourceId, pageNumber: citation.pageNumber, slideNumber: citation.slideNumber,
  }))
  return (await apiClient.post<AiSavedAnswer>(`/lms/courses/${lmsCourseId}/ai/saved-answers`, { question, answer, sources })).data
}

/** Deletes a saved answer for good. */
export async function deleteSavedAnswer(lmsCourseId: number, savedAnswerId: number): Promise<void> {
  await apiClient.delete(`/lms/courses/${lmsCourseId}/ai/saved-answers/${savedAnswerId}`)
}

/** A summary of one published page, module, or file in a style (v0.15.2). */
export async function summarizeCourse(
  lmsCourseId: number,
  input: { scope: AiSummaryScope; id: number; style: AiSummaryStyle },
  signal?: AbortSignal,
): Promise<AiSummary> {
  return (await apiClient.post<AiSummary>(`/lms/courses/${lmsCourseId}/ai/summary`, input, { signal })).data
}

/** The student's flashcard sets in the course (v0.15.3). */
export async function getFlashcardSets(lmsCourseId: number, signal?: AbortSignal): Promise<AiFlashcardSetSummary[]> {
  return (await apiClient.get<AiFlashcardSetSummary[]>(`/lms/courses/${lmsCourseId}/ai/flashcard-sets`, { signal })).data
}

export async function getFlashcardSet(lmsCourseId: number, setId: number, signal?: AbortSignal): Promise<AiFlashcardSet> {
  return (await apiClient.get<AiFlashcardSet>(`/lms/courses/${lmsCourseId}/ai/flashcard-sets/${setId}`, { signal })).data
}

/** Makes a set with AI from a page, module, file, or the whole course. */
export async function createFlashcardSet(
  lmsCourseId: number,
  input: { scope: 'COURSE' | 'TOPIC' | 'MODULE' | 'DOCUMENT'; id: number | null; cardCount: number },
  signal?: AbortSignal,
): Promise<AiFlashcardSet> {
  return (await apiClient.post<AiFlashcardSet>(`/lms/courses/${lmsCourseId}/ai/flashcard-sets`, input, { signal })).data
}

export async function reviewFlashcard(lmsCourseId: number, setId: number, cardId: number, rating: ReviewRating): Promise<AiFlashcard> {
  return (await apiClient.post<AiFlashcard>(`/lms/courses/${lmsCourseId}/ai/flashcard-sets/${setId}/cards/${cardId}/review`, { rating })).data
}

/** Deletes a set and its cards for good. */
export async function deleteFlashcardSet(lmsCourseId: number, setId: number): Promise<void> {
  await apiClient.delete(`/lms/courses/${lmsCourseId}/ai/flashcard-sets/${setId}`)
}

