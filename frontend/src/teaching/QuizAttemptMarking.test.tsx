import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/lmsQuizzesApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { QuizAttemptReview } from '../types/lms'
import { QuizAttemptMarking } from './QuizAttemptMarking'

vi.mock('../api/lmsQuizzesApi', () => ({ getQuizAttemptReview: vi.fn(), markQuizAnswer: vi.fn(), closeQuizAttempt: vi.fn() }))

const review: QuizAttemptReview = {
  attemptId: 9,
  quizId: 501,
  lmsCourseId: 8,
  quizTitle: 'Stacks check',
  quizPublished: true,
  studentLoginId: '2408400100011',
  attemptNumber: 1,
  state: 'SUBMITTED',
  startedAt: '2026-11-10T05:00:00Z',
  deadlineAt: null,
  submittedAt: '2026-11-10T05:10:00Z',
  late: false,
  negativeMarkPercent: 0,
  totalMarks: 6,
  automaticMarks: 2,
  teacherMarks: 0,
  score: null,
  questions: [
    {
      quizQuestionId: 601,
      number: 1,
      type: 'SINGLE_CHOICE',
      text: 'Which is first in, first out?',
      textFormat: 'PLAIN',
      marks: 2,
      options: [
        { text: 'Stack', correct: false },
        { text: 'Queue', correct: true },
      ],
      trueFalseAnswer: null,
      answerGuidance: null,
      explanation: null,
      answer: { quizQuestionId: 601, selectedOptions: [2], trueFalseAnswer: null, text: null, answeredAt: '2026-11-10T05:01:00Z' },
      outcome: 'CORRECT',
      automaticMarks: 2,
      mark: null,
    },
    {
      quizQuestionId: 602,
      number: 2,
      type: 'DESCRIPTIVE',
      text: 'Explain a queue.',
      textFormat: 'PLAIN',
      marks: 4,
      options: [],
      trueFalseAnswer: null,
      answerGuidance: 'First in, first out.',
      explanation: null,
      answer: { quizQuestionId: 602, selectedOptions: [], trueFalseAnswer: null, text: 'It serves in order.', answeredAt: '2026-11-10T05:05:00Z' },
      outcome: 'NEEDS_MARKING',
      automaticMarks: null,
      mark: null,
    },
  ],
}

function renderMarking(given: QuizAttemptReview = review) {
  vi.mocked(api.getQuizAttemptReview).mockResolvedValue(given)
  return render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>
        <QuizAttemptMarking lmsCourseId={8} attemptId={9} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('QuizAttemptMarking', () => {
  it('shows the student answer with the key and marks a written answer within its marks', async () => {
    vi.mocked(api.markQuizAnswer).mockResolvedValue(review)
    const user = userEvent.setup()
    renderMarking()

    expect(await screen.findByText('It serves in order.')).toBeInTheDocument()
    expect(screen.getByText('Answered: Queue')).toBeInTheDocument()
    expect(screen.getByText('Guidance: First in, first out.')).toBeInTheDocument()
    expect(screen.getByText('Waiting for marking')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Marks for question 2'), '5')
    await user.click(screen.getByRole('button', { name: /^Save marks\s*for question 2$/ }))
    expect(screen.getByText('Enter marks from 0 to 4, with at most 2 decimal places.')).toBeInTheDocument()
    expect(api.markQuizAnswer).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('Marks for question 2'))
    await user.type(screen.getByLabelText('Marks for question 2'), '3.5')
    await user.type(screen.getByLabelText('Feedback for question 2 (optional)'), 'Say why.')
    await user.click(screen.getByRole('button', { name: /^Save marks\s*for question 2$/ }))
    expect(api.markQuizAnswer).toHaveBeenCalledWith(9, 602, 3.5, 'Say why.')
  })

  it('offers to close an attempt whose time is up, and no marking before', async () => {
    vi.mocked(api.closeQuizAttempt).mockResolvedValue(review)
    const user = userEvent.setup()
    renderMarking({ ...review, state: 'TIME_UP', submittedAt: null })

    await user.click(await screen.findByRole('button', { name: 'Close attempt' }))
    expect(api.closeQuizAttempt).toHaveBeenCalledWith(9)
    expect(screen.queryByLabelText('Marks for question 2')).not.toBeInTheDocument()
  })
})
