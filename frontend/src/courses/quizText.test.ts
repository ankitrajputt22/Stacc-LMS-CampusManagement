import { describe, expect, it } from 'vitest'
import { attemptsText, clockText, inputOfDraft, isAnswered, quizStatusText } from './quizText'
import type { StudentQuiz } from '../types/lms'

const quiz = {
  availability: 'OPEN',
  availableFrom: null,
  openAttemptId: null,
  attemptsLeft: 1,
  maxAttempts: 2,
  attempts: [{ id: 1 }],
} as unknown as StudentQuiz

describe('quiz text', () => {
  it('says whether the quiz can start, as the backend decided', () => {
    expect(quizStatusText(quiz)).toBe('Open.')
    expect(quizStatusText({ ...quiz, openAttemptId: 9 })).toBe('You have an attempt in progress.')
    expect(quizStatusText({ ...quiz, attemptsLeft: 0 })).toBe('You have used every attempt.')
    expect(quizStatusText({ ...quiz, availability: 'CLOSED' })).toBe('This quiz has closed.')
    expect(quizStatusText({ ...quiz, availability: 'NOT_OPEN' })).toBe('Not open yet.')
  })

  it('counts attempts', () => {
    expect(attemptsText(quiz)).toBe('1 of 2 attempts used')
    expect(attemptsText({ ...quiz, maxAttempts: null })).toBe('1 attempt used (unlimited)')
  })

  it('shows the time left as a clock', () => {
    expect(clockText(725)).toBe('12:05')
    expect(clockText(3729)).toBe('1:02:09')
    expect(clockText(-4)).toBe('0:00')
  })

  it('sends only the part of an answer the question takes', () => {
    const draft = { selected: [3, 1], trueFalse: true, text: '  ' }
    expect(inputOfDraft({ type: 'MULTIPLE_CHOICE' }, draft)).toEqual({ selectedOptions: [1, 3], trueFalseAnswer: null, text: null })
    expect(inputOfDraft({ type: 'TRUE_FALSE' }, draft)).toEqual({ selectedOptions: [], trueFalseAnswer: true, text: null })
    expect(inputOfDraft({ type: 'SHORT_ANSWER' }, draft)).toEqual({ selectedOptions: [], trueFalseAnswer: null, text: null })
    expect(isAnswered({ type: 'SHORT_ANSWER' }, draft)).toBe(false)
    expect(isAnswered({ type: 'SINGLE_CHOICE' }, draft)).toBe(true)
  })
})
