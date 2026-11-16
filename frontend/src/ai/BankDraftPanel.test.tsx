import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { testQueryClient } from '../test/queryTestUtils'
import { BankDraftPanel } from './BankDraftPanel'

vi.mock('../api/aiApi')

function renderPanel() {
  render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>
        <BankDraftPanel lmsCourseId={3} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('BankDraftPanel', () => {
  it('drafts the chosen types into the bank and says what was dropped', async () => {
    vi.mocked(api.draftIntoQuestionBank).mockResolvedValue({
      added: 3, droppedForKeyMismatch: 1, droppedAsRepeats: 0, leftOut: 0, insufficientContext: false, questionIds: [1, 2, 3],
    })
    const user = userEvent.setup()
    renderPanel()

    await user.click(screen.getByRole('checkbox', { name: 'True or false' }))
    await user.click(screen.getByRole('checkbox', { name: 'Descriptive' }))
    await user.click(screen.getByRole('button', { name: 'Draft questions' }))

    expect(api.draftIntoQuestionBank).toHaveBeenCalledWith(3,
      { questionCount: 5, questionTypes: ['SINGLE_CHOICE', 'DESCRIPTIVE'], difficulty: 'MEDIUM', topicId: null }, expect.anything())
    expect(await screen.findByText('Added 3 questions to the question bank as Needs review.')).toBeInTheDocument()
    expect(screen.getByText(/1 question was dropped/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Review them in the question bank' })).toHaveAttribute('href', '/teaching/3/questions')
  })

  it('asks for at least one type before sending', async () => {
    const user = userEvent.setup()
    renderPanel()
    await user.click(screen.getByRole('checkbox', { name: 'Single choice' }))
    await user.click(screen.getByRole('checkbox', { name: 'True or false' }))
    await user.click(screen.getByRole('button', { name: 'Draft questions' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Choose at least one question type.')
    expect(api.draftIntoQuestionBank).not.toHaveBeenCalled()
  })
})
