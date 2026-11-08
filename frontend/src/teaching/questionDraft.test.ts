import { describe, expect, it } from 'vitest'
import { changeType, checkDraft, emptyDraft, inputOf, type QuestionDraft } from './questionDraft'

function draft(change: Partial<QuestionDraft>): QuestionDraft {
  return { ...emptyDraft(), text: 'Which normal form?', ...change }
}

describe('question drafts', () => {
  it('catches the obvious slips of each type before sending', () => {
    expect(checkDraft(draft({ text: ' ' })).text).toBe('Write the question.')
    expect(checkDraft(draft({ marks: '0' })).marks).toBe('Enter marks greater than zero.')
    expect(checkDraft(draft({ marks: '1.234' })).marks).toBe('Use at most two decimal places, below 1,000,000.')
    expect(
      checkDraft(draft({ options: [{ text: 'A', correct: true }, { text: 'B', correct: true }] })).options,
    ).toBe('Mark exactly one correct option.')
    expect(
      checkDraft(draft({ type: 'MULTIPLE_CHOICE', options: [{ text: 'A', correct: false }, { text: 'B', correct: false }] })).options,
    ).toBe('Mark at least one correct option.')
    expect(checkDraft(draft({ options: [{ text: 'A', correct: true }, { text: ' a ', correct: false }] })).options).toBe(
      'Options must differ from each other.',
    )
    expect(checkDraft(draft({ type: 'TRUE_FALSE' })).trueFalseAnswer).toBe('Choose the correct answer.')
    expect(checkDraft(draft({ options: [{ text: 'A', correct: true }, { text: 'B', correct: false }] }))).toEqual({})
  })

  it('sends only the parts the type takes', () => {
    const trueFalse = inputOf(draft({ type: 'TRUE_FALSE', trueFalseAnswer: 'false', answerGuidance: 'ignored', topic: ' ' }))
    expect(trueFalse.options).toEqual([])
    expect(trueFalse.trueFalseAnswer).toBe(false)
    expect(trueFalse.answerGuidance).toBeNull()
    expect(trueFalse.topic).toBeNull()

    const descriptive = inputOf(draft({ type: 'DESCRIPTIVE', answerGuidance: 'Mention keys.', marks: '5', outcomeId: '91' }))
    expect(descriptive).toMatchObject({ options: [], trueFalseAnswer: null, answerGuidance: 'Mention keys.', marks: 5, outcomeId: 91 })
  })

  it('keeps the options fitting when the type changes', () => {
    const multiple = draft({ type: 'MULTIPLE_CHOICE', options: [{ text: 'A', correct: true }, { text: 'B', correct: true }] })
    expect(changeType(multiple, 'SINGLE_CHOICE').options.map((option) => option.correct)).toEqual([true, false])
    expect(changeType(multiple, 'SHORT_ANSWER').options.every((option) => option.correct)).toBe(true)
  })
})
