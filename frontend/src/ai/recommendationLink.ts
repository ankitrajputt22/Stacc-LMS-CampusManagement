import type { AiRecommendation } from '../types/ai'

/** Where a recommendation leads, or null when it names nothing to open. The page itself checks access again. */
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
