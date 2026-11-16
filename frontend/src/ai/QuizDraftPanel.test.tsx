import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { testQueryClient } from '../test/queryTestUtils'
import { QuizDraftPanel } from './QuizDraftPanel'

vi.mock('../api/aiApi')

describe('QuizDraftPanel', () => {
  it('builds a draft quiz and says where its questions came from', async () => {
    vi.mocked(api.draftQuiz).mockResolvedValue({ quizId: 77, fromBank: 6, fromAi: 2, missing: 2, aiNote: null })
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={testQueryClient()}>
        <MemoryRouter>
          <QuizDraftPanel lmsCourseId={3} />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    await user.type(screen.getByRole('textbox', { name: 'Quiz title' }), 'Unit 2 test')
    await user.click(screen.getByRole('checkbox', { name: 'Let AI draft questions the bank is short of' }))
    await user.click(screen.getByRole('button', { name: 'Build draft quiz' }))
    expect(api.draftQuiz).toHaveBeenCalledWith(3, {
      title: 'Unit 2 test', type: 'GRADED', questionCount: 10, questionTypes: ['SINGLE_CHOICE', 'TRUE_FALSE'], difficulty: null,
      fillGapsWithAi: true,
    }, expect.anything())
    expect(await screen.findByText('Draft quiz made with 6 reviewed bank questions and 2 AI drafts that need review.')).toBeInTheDocument()
    expect(screen.getByText('2 questions are still missing.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open the draft quiz' })).toHaveAttribute('href', '/teaching/3/quizzes/77')
  })
})
