import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/lmsQuizAttemptsApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { QuizAttempt } from '../types/lms'
import { QuizTaking } from './QuizTaking'

vi.mock('../api/lmsQuizAttemptsApi', () => ({ saveQuizAnswer: vi.fn(), submitQuizAttempt: vi.fn() }))

const now = new Date()

const attempt: QuizAttempt = {
  id: 9,
  quizId: 501,
  lmsCourseId: 8,
  quizTitle: 'Stacks check',
  quizType: 'GRADED',
  attemptNumber: 1,
  state: 'IN_PROGRESS',
  startedAt: now.toISOString(),
  deadlineAt: new Date(now.getTime() + 10 * 60_000).toISOString(),
  submittedAt: null,
  late: false,
  serverTime: now.toISOString(),
  totalMarks: 3,
  questions: [
    {
      quizQuestionId: 601,
      number: 1,
      type: 'SINGLE_CHOICE',
      text: 'Which structure is first in, first out?',
      textFormat: 'PLAIN',
      marks: 2,
      options: [
        { position: 2, text: 'Queue' },
        { position: 1, text: 'Stack' },
      ],
      answer: null,
    },
    {
      quizQuestionId: 602,
      number: 2,
      type: 'TRUE_FALSE',
      text: 'A stack is last in, first out.',
      textFormat: 'PLAIN',
      marks: 1,
      options: [],
      answer: { quizQuestionId: 602, selectedOptions: [], trueFalseAnswer: true, text: null, answeredAt: now.toISOString() },
    },
  ],
}

function renderTaking(given: QuizAttempt = attempt) {
  return render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>
        <QuizTaking attempt={given} lmsCourseId={8} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('QuizTaking', () => {
  beforeEach(() => {
    vi.mocked(api.saveQuizAnswer).mockResolvedValue({
      quizQuestionId: 601,
      selectedOptions: [2],
      trueFalseAnswer: null,
      text: null,
      answeredAt: now.toISOString(),
    })
  })

  it('saves a choice at once and submits after confirming', async () => {
    vi.mocked(api.submitQuizAttempt).mockResolvedValue({ ...attempt, state: 'SUBMITTED', submittedAt: now.toISOString() })
    const user = userEvent.setup()
    renderTaking()

    expect(screen.getByText(/Time left (9:5\d|10:00)/)).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'True' })).toBeChecked()
    expect(screen.getByText('1 of 2 questions answered.')).toBeInTheDocument()

    await user.click(screen.getByRole('radio', { name: 'Queue' }))
    expect(api.saveQuizAnswer).toHaveBeenCalledWith(9, 601, { selectedOptions: [2], trueFalseAnswer: null, text: null })
    expect(await screen.findByText('Saved')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Submit quiz' }))
    expect(api.submitQuizAttempt).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Submit' }))
    expect(await screen.findByText('Quiz submitted successfully.')).toBeInTheDocument()
    expect(api.submitQuizAttempt).toHaveBeenCalledWith(9)
    expect(screen.getByRole('radio', { name: 'Queue' })).toBeDisabled()
    expect(screen.getByRole('link', { name: 'Back to the quiz' })).toHaveAttribute('href', '/courses/8/quizzes/501')
  })

  it('hands in the saved answers once the server deadline has passed', async () => {
    vi.mocked(api.submitQuizAttempt).mockResolvedValue({ ...attempt, state: 'EXPIRED', submittedAt: attempt.startedAt })
    renderTaking({ ...attempt, deadlineAt: new Date(now.getTime() - 1000).toISOString() })

    await waitFor(() => expect(api.submitQuizAttempt).toHaveBeenCalledTimes(1))
    expect(await screen.findByText('Time is up. The answers saved before the deadline were handed in.')).toBeInTheDocument()
  })

  it('shows a handed-in attempt read-only', () => {
    renderTaking({ ...attempt, state: 'SUBMITTED', submittedAt: now.toISOString() })

    expect(screen.queryByRole('button', { name: 'Submit quiz' })).not.toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'True' })).toBeDisabled()
    expect(screen.queryByText(/Time left/)).not.toBeInTheDocument()
  })
})
