import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { SummaryPanel } from './SummaryPanel'

vi.mock('../api/aiApi')

const choices = {
  modules: [{ id: 7, title: 'Unit 2: SQL' }],
  topics: [{ id: 31, title: 'Joins', moduleTitle: 'Unit 2: SQL' }],
  files: [{ id: 20, title: 'Join examples', pageTitle: 'Joins' }],
}

describe('SummaryPanel', () => {
  it('summarises a file as revision notes, with sources', async () => {
    vi.mocked(api.summarizeCourse).mockResolvedValue({
      scope: 'DOCUMENT', id: 20, title: 'Join examples', style: 'REVISION_NOTES', summary: 'Remember: joins combine rows.',
      keyPoints: ['INNER JOIN keeps matches'], insufficientContext: false,
      citations: [{ sourceType: 'STUDY_MATERIAL', sourceId: 20, title: 'Join examples', fileName: 'joins.pdf', pageNumber: 2, slideNumber: null, pageId: 31 }],
    })
    const user = userEvent.setup()
    render(<MemoryRouter><SummaryPanel lmsCourseId={3} choices={choices} /></MemoryRouter>)
    await user.selectOptions(screen.getByRole('combobox', { name: 'Summarise' }), 'A file')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Style' }), 'Revision notes')
    await user.click(screen.getByRole('button', { name: 'Summarise' }))
    expect(api.summarizeCourse).toHaveBeenCalledWith(3, { scope: 'DOCUMENT', id: 20, style: 'REVISION_NOTES' }, expect.anything())
    expect(await screen.findByText('Remember: joins combine rows.')).toBeInTheDocument()
    expect(screen.getByText('INNER JOIN keeps matches')).toBeInTheDocument()
  })

  it('summarises a whole module', async () => {
    vi.mocked(api.summarizeCourse).mockResolvedValue({
      scope: 'MODULE', id: 7, title: null, style: 'BRIEF', summary: null, keyPoints: [], insufficientContext: true, citations: [],
    })
    const user = userEvent.setup()
    render(<MemoryRouter><SummaryPanel lmsCourseId={3} choices={choices} /></MemoryRouter>)
    await user.selectOptions(screen.getByRole('combobox', { name: 'Summarise' }), 'A module')
    await user.click(screen.getByRole('button', { name: 'Summarise' }))
    expect(api.summarizeCourse).toHaveBeenCalledWith(3, { scope: 'MODULE', id: 7, style: 'BRIEF' }, expect.anything())
  })
})
