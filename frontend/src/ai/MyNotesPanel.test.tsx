import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { AiStudentNote } from '../types/ai'
import { MyNotesPanel } from './MyNotesPanel'

vi.mock('../api/aiApi')

const note: AiStudentNote = {
  id: 4, title: 'My summary', content: 'Normal forms remove redundancy.', createdAt: '2026-11-15T05:00:00Z', updatedAt: '2026-11-15T05:00:00Z',
}

describe('MyNotesPanel', () => {
  it('deletes a note only after confirming', async () => {
    vi.mocked(api.getMyNotes).mockResolvedValueOnce([note]).mockResolvedValue([])
    vi.mocked(api.deleteMyNote).mockResolvedValue()
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={testQueryClient()}>
        <MyNotesPanel lmsCourseId={3} />
      </QueryClientProvider>,
    )

    await user.click(await screen.findByRole('button', { name: /^Delete/ }))
    expect(api.deleteMyNote).not.toHaveBeenCalled()
    expect(screen.getByText(/This cannot be undone/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Delete note' }))
    expect(api.deleteMyNote).toHaveBeenCalledWith(3, 4)
    expect(await screen.findByText('No notes yet.')).toBeInTheDocument()
  })
})
