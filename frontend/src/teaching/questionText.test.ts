import { describe, expect, it } from 'vitest'
import type { LmsQuestion } from '../types/lms'
import { filterQuestions, NO_FILTERS, questionName, questionTopics } from './questionText'

function question(id: number, change: Partial<LmsQuestion>): LmsQuestion {
  return {
    id,
    type: 'SINGLE_CHOICE',
    text: 'Question',
    textFormat: 'PLAIN',
    explanation: null,
    options: [],
    trueFalseAnswer: null,
    answerGuidance: null,
    topic: null,
    difficulty: 'MEDIUM',
    marks: 1,
    bloomLevel: null,
    outcome: null,
    reviewStatus: 'NEEDS_REVIEW',
    practiceAllowed: false,
    status: 'ACTIVE',
    inPublishedQuiz: false,
    updatedAt: '2026-11-08T10:00:00Z',
    ...change,
  }
}

describe('question bank filters', () => {
  const bank = [
    question(1, { text: 'Define a primary key.', topic: 'SQL', difficulty: 'EASY' }),
    question(2, { text: 'Which normal form?', topic: 'Normalization', type: 'TRUE_FALSE' }),
    question(3, { text: 'Old KEY question', topic: 'SQL', status: 'ARCHIVED' }),
  ]

  it('shows active questions by default and combines every filter', () => {
    expect(filterQuestions(bank, NO_FILTERS).map((found) => found.id)).toEqual([1, 2])
    expect(filterQuestions(bank, { ...NO_FILTERS, search: 'key', status: '' }).map((found) => found.id)).toEqual([1, 3])
    expect(filterQuestions(bank, { ...NO_FILTERS, topic: 'SQL', difficulty: 'EASY' }).map((found) => found.id)).toEqual([1])
    expect(filterQuestions(bank, { ...NO_FILTERS, type: 'TRUE_FALSE' }).map((found) => found.id)).toEqual([2])
  })

  it('lists each topic once and names questions by their first words', () => {
    expect(questionTopics(bank)).toEqual(['Normalization', 'SQL'])
    expect(questionName({ text: `${'word '.repeat(20)}end` })).toHaveLength(58)
    expect(questionName({ text: '  Short\n question ' })).toBe('Short question')
  })
})
