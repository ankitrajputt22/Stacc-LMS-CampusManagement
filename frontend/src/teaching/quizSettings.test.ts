import { describe, expect, it } from 'vitest'
import { checkSettings, emptySettings, quizFacts, settingsOf } from './quizSettings'
import type { LmsQuiz } from '../types/lms'

describe('checkSettings', () => {
  it('sends empty optional settings as no limit and times as moments', () => {
    const { errors, input } = checkSettings({
      ...emptySettings(),
      title: '  Unit test  ',
      maxAttempts: '',
      dueAt: '2026-11-21T23:59',
      negativeMarkPercent: '',
    })

    expect(errors).toEqual({})
    expect(input).toMatchObject({
      title: 'Unit test',
      instructions: null,
      type: 'GRADED',
      timeLimitMinutes: null,
      maxAttempts: null,
      availableFrom: null,
      availableUntil: null,
      negativeMarkPercent: 0,
    })
    expect(input?.dueAt).toBe(new Date(2026, 10, 21, 23, 59).toISOString())
  })

  it('catches the same slips as the backend', () => {
    const { errors, input } = checkSettings({
      ...emptySettings(),
      timeLimitMinutes: '0',
      maxAttempts: '1.5',
      availableFrom: '2026-11-21T10:00',
      dueAt: '2026-11-20T10:00',
      availableUntil: '2026-11-21T10:00',
      negativeMarkPercent: '100.5',
    })

    expect(input).toBeNull()
    expect(errors).toEqual({
      title: 'Enter a title.',
      timeLimitMinutes: 'Enter whole minutes from 1 to 600, or leave it empty.',
      maxAttempts: 'Enter a whole number from 1 to 100, or leave it empty.',
      availableUntil: 'The closing time must be after the opening time.',
      dueAt: 'The due date cannot be before the opening time.',
      negativeMarkPercent: 'Enter a percentage from 0 to 100, with at most 2 decimal places.',
    })
  })

  it('refuses a due date after the closing time', () => {
    const { errors } = checkSettings({
      ...emptySettings(),
      title: 'Q',
      dueAt: '2026-11-22T10:00',
      availableUntil: '2026-11-21T10:00',
    })
    expect(errors).toEqual({ dueAt: 'The due date cannot be after the closing time.' })
  })
})

describe('quiz settings', () => {
  it('reads a saved quiz back into the form', () => {
    const quiz = {
      title: 'Unit test',
      instructions: null,
      instructionsFormat: 'PLAIN',
      type: 'PRACTICE',
      timeLimitMinutes: 20,
      availableFrom: null,
      dueAt: null,
      availableUntil: null,
      maxAttempts: null,
      shuffleQuestions: true,
      shuffleOptions: false,
      negativeMarkPercent: 25,
    } as unknown as LmsQuiz

    expect(settingsOf(quiz)).toEqual({
      ...emptySettings(),
      title: 'Unit test',
      instructionsFormat: 'PLAIN',
      type: 'PRACTICE',
      timeLimitMinutes: '20',
      maxAttempts: '',
      shuffleQuestions: true,
      negativeMarkPercent: '25',
    })
  })

  it('says the size and rules in a few words', () => {
    expect(quizFacts({ questionCount: 1, totalMarks: 2.5, timeLimitMinutes: null, maxAttempts: 1 })).toBe(
      '1 question · 2.5 marks · No time limit · 1 attempt',
    )
    expect(quizFacts({ questionCount: 3, totalMarks: 1, timeLimitMinutes: 20, maxAttempts: null })).toBe(
      '3 questions · 1 mark · 20 minutes · Unlimited attempts',
    )
  })
})
