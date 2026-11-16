import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { testQueryClient } from '../test/queryTestUtils'
import { SavedAnswersPanel } from './SavedAnswersPanel'

vi.mock('../api/aiApi')

describe('SavedAnswersPanel', () => {
  it('lists saved answers and deletes one for good only after confirming', async () => {
    vi.mocked(api.getSavedAnswers)
      .mockResolvedValueOnce([{ id: 5, question: 'What is 3NF?', answer: 'It removes transitive dependencies.', citations: [],
        savedAt: '2026-11-16T05:00:00Z' }])
      .mockResolvedValue([])
    vi.mocked(api.deleteSavedAnswer).mockResolvedValue()
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={testQueryClient()}>
        <MemoryRouter><SavedAnswersPanel lmsCourseId={3} /></MemoryRouter>
      </QueryClientProvider>,
    )
    expect(await screen.findByText('It removes transitive dependencies.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /^Delete/ }))
    expect(api.deleteSavedAnswer).not.toHaveBeenCalled()
    expect(screen.getByText(/cannot be undone/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Delete saved answer' }))
    expect(api.deleteSavedAnswer).toHaveBeenCalledWith(3, 5)
    expect(await screen.findByText(/No saved answers yet/)).toBeInTheDocument()
  })
})
