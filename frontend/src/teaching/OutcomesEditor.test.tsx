import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/lmsOutcomesApi'
import { testQueryClient } from '../test/queryTestUtils'
import { OutcomesEditor } from './OutcomesEditor'

vi.mock('../api/lmsOutcomesApi', () => ({
  getFacultyOutcomes: vi.fn(),
  addOutcome: vi.fn(),
  describeOutcome: vi.fn(),
  archiveOutcome: vi.fn(),
}))

describe('OutcomesEditor', () => {
  it('adds an outcome, and archives one only after confirmation', async () => {
    vi.mocked(api.getFacultyOutcomes).mockResolvedValue([
      { id: 91, code: 'CO1', description: 'Analyse costs.', status: 'ACTIVE' },
      { id: 92, code: 'CO2', description: 'Old outcome.', status: 'ARCHIVED' },
    ])
    vi.mocked(api.addOutcome).mockResolvedValue({ id: 93, code: 'CO3', description: 'Design algorithms.', status: 'ACTIVE' })
    vi.mocked(api.archiveOutcome).mockResolvedValue({ id: 91, code: 'CO1', description: 'Analyse costs.', status: 'ARCHIVED' })
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={testQueryClient()}>
        <OutcomesEditor lmsCourseId={8} />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Analyse costs.')).toBeInTheDocument()
    expect(screen.getByText('Archived')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /^Archive/ })).toHaveLength(1)

    await user.type(screen.getByLabelText('New outcome'), 'Design algorithms.')
    await user.click(screen.getByRole('button', { name: 'Add outcome' }))
    expect(api.addOutcome).toHaveBeenCalledWith(8, 'Design algorithms.')

    await user.click(screen.getByRole('button', { name: /^Archive\s*CO1$/ }))
    expect(api.archiveOutcome).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Archive outcome' }))
    expect(api.archiveOutcome).toHaveBeenCalledWith(91)
  })
})
