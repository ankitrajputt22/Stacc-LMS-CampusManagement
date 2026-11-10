import { describe, expect, it } from 'vitest'
import type { ExamPaperQuestion } from '../types/lms'
import { answerKeyText, blueprintRowText, marksText, parseMarks, sectionGroups } from './examPaperText'

function question(overrides: Partial<ExamPaperQuestion>): ExamPaperQuestion {
  return {
    id: 1,
    questionId: 11,
    position: 1,
    sectionTitle: null,
    marks: 2,
    type: 'SINGLE_CHOICE',
    text: 'Which structure is first in, first out?',
    textFormat: 'PLAIN',
    explanation: null,
    options: [
      { text: 'Stack', correct: false },
      { text: 'Queue', correct: true },
    ],
    trueFalseAnswer: null,
    answerGuidance: null,
    topic: null,
    difficulty: 'EASY',
    bloomLevel: null,
    outcomeCode: null,
    reviewStatus: 'REVIEWED',
    status: 'ACTIVE',
    ...overrides,
  }
}

describe('exam paper text', () => {
  it('reads marks only when they are above zero with at most two decimals', () => {
    expect(parseMarks('2.5')).toBe(2.5)
    expect(parseMarks(' 4 ')).toBe(4)
    expect(parseMarks('0')).toBeNull()
    expect(parseMarks('1.234')).toBeNull()
    expect(parseMarks('two')).toBeNull()
    expect(marksText(1)).toBe('1 mark')
    expect(marksText(2.5)).toBe('2.5 marks')
  })

  it('describes a blueprint row with its filters, or any question without them', () => {
    const row = { position: 1, sectionTitle: 'A', questionType: null, difficulty: null, bloomLevel: null, outcomeId: null, topic: null, questionCount: 1, marksEach: 5 }
    expect(blueprintRowText(row, [])).toBe('1 question × 5 marks: any reviewed question')
    expect(
      blueprintRowText({ ...row, questionType: 'TRUE_FALSE', difficulty: 'HARD', outcomeId: 4, topic: 'Stacks', questionCount: 3, marksEach: 1 }, [
        { id: 4, code: 'CO2', description: 'Use stacks', status: 'ACTIVE' },
      ]),
    ).toBe('3 questions × 1 mark: True or false, Hard, CO2, topic “Stacks”')
  })

  it('writes the answer key for each kind of question', () => {
    expect(answerKeyText(question({}))).toBe('(b) Queue')
    expect(answerKeyText(question({ type: 'TRUE_FALSE', options: [], trueFalseAnswer: false }))).toBe('False')
    expect(answerKeyText(question({ type: 'SHORT_ANSWER', options: [{ text: 'FIFO', correct: true }, { text: 'first in first out', correct: true }] }))).toBe(
      'Accepted: FIFO; first in first out',
    )
    expect(answerKeyText(question({ type: 'DESCRIPTIVE', options: [] }))).toBe('No marking guidance given.')
  })

  it('groups questions by section in order, starting a new group when the section changes', () => {
    const groups = sectionGroups([
      question({ id: 1, sectionTitle: 'A' }),
      question({ id: 2, sectionTitle: 'A' }),
      question({ id: 3, sectionTitle: 'B' }),
      question({ id: 4, sectionTitle: 'A' }),
    ])
    expect(groups.map((group) => [group.section, group.questions.length])).toEqual([
      ['A', 2],
      ['B', 1],
      ['A', 1],
    ])
  })
})
