import { getApiError, getHttpStatus } from '../api/apiError'
import type { AiCitation, AiDifficulty, AiKnowledgeIndexStatus, AiQuestionType } from '../types/ai'

export const AI_UNAVAILABLE = 'AI learning features are temporarily unavailable.'
export const STUDY_NOT_READY = 'AI study content is not ready for this course yet.'
export const INSUFFICIENT_CONTEXT =
  'I couldn’t find enough information in the published course materials to answer this reliably.'
export const DRAFT_WARNING = 'AI-generated draft. Review and edit every question before use.'

export const KNOWLEDGE_STATUS_LABELS: Record<AiKnowledgeIndexStatus, string> = {
  NOT_INDEXED: 'Not indexed',
  BUILDING: 'Building',
  READY: 'Ready',
  FAILED: 'Failed',
}

export const DIFFICULTY_LABELS: Record<AiDifficulty, string> = {
  EASY: 'Easy',
  MEDIUM: 'Medium',
  HARD: 'Hard',
  MIXED: 'Mixed',
}

export const QUESTION_TYPE_LABELS: Record<AiQuestionType, string> = {
  MCQ: 'Multiple choice',
  SHORT_ANSWER: 'Short answer',
  LONG_ANSWER: 'Long answer',
  MIXED: 'Mixed',
}

/** "Topic", or "Study Material · Page 4" / "Study Material · Slide 2". */
export function citationKind(citation: AiCitation): string {
  if (citation.sourceType === 'TOPIC') {
    return 'Topic'
  }
  const place =
    citation.pageNumber !== null
      ? ` · Page ${citation.pageNumber}`
      : citation.slideNumber !== null
        ? ` · Slide ${citation.slideNumber}`
        : ''
  return `Study Material${place}`
}

export type AiFailure = {
  kind: 'unavailable' | 'notReady' | 'notFound' | 'denied' | 'invalid' | 'failed'
  message: string
}

/**
 * What to say when an AI request fails, in plain words. A 401 is handled by the shared client (the session ends), a 403
 * never signs out, and nothing names the AI service, model, or index.
 */
export function aiFailure(error: unknown, audience: 'student' | 'faculty'): AiFailure | null {
  const status = getHttpStatus(error)
  const message = getApiError(error)?.message
  switch (status) {
    case 401:
      return null
    case 502:
    case 503:
      return { kind: 'unavailable', message: AI_UNAVAILABLE }
    case 409:
      return {
        kind: 'notReady',
        message: audience === 'student' ? STUDY_NOT_READY : (message ?? 'AI knowledge is not ready for this course.'),
      }
    case 404:
      return { kind: 'notFound', message: 'This course or topic is not available to your account.' }
    case 403:
      return { kind: 'denied', message: 'You don’t have access to this AI feature.' }
    case 400:
      return { kind: 'invalid', message: message ?? 'Check what you entered and try again.' }
    case null:
      return { kind: 'failed', message: 'Couldn’t reach Stacc. Check your connection and try again.' }
    default:
      return { kind: 'failed', message: 'Something went wrong. Try again.' }
  }
}
