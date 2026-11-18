import type { AiRecommendation } from '../types/ai'

export function recommendationLink(lmsCourseId: number, item: AiRecommendation): string | null {
  if (item.kind === 'REVIEW_FLASHCARDS') {
    return `/courses/${lmsCourseId}/ai/flashcards`
  }
  if (item.targetId === null) {
    return null
  }
  return item.kind === 'ASSIGNMENT_SOON'
    ? `/courses/${lmsCourseId}/assignments/${item.targetId}`
    : `/courses/${lmsCourseId}/pages/${item.targetId}`
}
