import { describe, expect, it } from 'vitest'
import type { AiRecommendation } from '../types/ai'
import { recommendationLink } from './recommendationLink'

const item = (kind: AiRecommendation['kind'], targetId: number | null): AiRecommendation =>
  ({ kind, targetId, title: 'Title', reason: 'Reason' }) as AiRecommendation

describe('recommendationLink', () => {
  it('links pages, assignments, and flashcards inside the course', () => {
    expect(recommendationLink(3, item('PRACTISE_WEAK', 9))).toBe('/courses/3/pages/9')
    expect(recommendationLink(3, item('REFRESH_PAGE', 9))).toBe('/courses/3/pages/9')
    expect(recommendationLink(3, item('ASSIGNMENT_SOON', 4))).toBe('/courses/3/assignments/4')
    expect(recommendationLink(3, item('REVIEW_FLASHCARDS', 2))).toBe('/courses/3/ai/flashcards')
  })

  it('links nothing without a target', () => {
    expect(recommendationLink(3, item('START_PAGE', null))).toBeNull()
  })
})
