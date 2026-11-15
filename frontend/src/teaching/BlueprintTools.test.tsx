import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/lmsExamPapersApi'
import { testQueryClient } from '../test/queryTestUtils'
import { BlueprintShortfall, PaperSetsForm } from './BlueprintTools'

vi.mock('../api/lmsExamPapersApi')

function wrap(node: React.ReactNode) {
  return render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>{node}</MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('Blueprint tools', () => {
  it('builds distinct sets', async () => {
    vi.mocked(api.buildExamPaperSets).mockResolvedValue([{}, {}, {}] as never)
    const done = vi.fn()
    const user = userEvent.setup()
    wrap(<PaperSetsForm blueprintId={9} onDone={done} />)
    await user.selectOptions(screen.getByRole('combobox', { name: 'Sets' }), '3')
    await user.click(screen.getByRole('button', { name: 'Build sets' }))
    expect(api.buildExamPaperSets).toHaveBeenCalledWith(9, { title: null, sets: 3, distinct: true })
    expect(done).toHaveBeenCalledWith('Built 3 sets as draft papers.')
  })

  it('shows what each row is short of and drafts it with AI for review', async () => {
    vi.mocked(api.getBlueprintShortfall).mockResolvedValue([
      { position: 1, sectionTitle: 'A', needed: 4, available: 4, missing: 0 },
      { position: 2, sectionTitle: 'B', needed: 3, available: 1, missing: 2 },
    ])
    vi.mocked(api.fillBlueprintShortfall).mockResolvedValue({
      rows: [{ position: 2, sectionTitle: 'B', missing: 2, added: 2, droppedForKeyMismatch: 0 }], note: null,
    })
    const user = userEvent.setup()
    wrap(<BlueprintShortfall lmsCourseId={3} blueprintId={9} onClose={vi.fn()} />)
    await user.click(screen.getByRole('button', { name: 'Check the bank' }))
    expect(await screen.findByText('Row 2 (B): needs 3, the bank has 1 — 2 missing')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Draft the missing questions with AI' }))
    expect(await screen.findByText(/AI drafted 2 questions into the bank as Needs review/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Review them in the question bank' })).toHaveAttribute('href', '/teaching/3/questions')
  })
})
