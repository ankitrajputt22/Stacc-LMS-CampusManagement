import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { AiStudentNote } from '../types/ai'
import { AskPanel } from './AskPanel'
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

describe('AskPanel', () => {
  it('asks the course alone by default and with my notes when chosen, labelling note sources', async () => {
    vi.mocked(api.askCourseAi).mockResolvedValue({
      answer: 'It removes redundancy.', insufficientContext: false,
      citations: [{ sourceType: 'NOTE', sourceId: 4, title: 'My summary', fileName: null, pageNumber: null, slideNumber: null }],
    })
    const user = userEvent.setup()
    render(<MemoryRouter><AskPanel lmsCourseId={3} /></MemoryRouter>)

    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'What does 3NF remove?')
    await user.click(screen.getByRole('button', { name: 'Ask' }))
    expect(api.askCourseAi).toHaveBeenLastCalledWith(3, 'What does 3NF remove?', expect.anything(), { mode: 'COURSE', moduleId: null })

    await user.click(screen.getByRole('radio', { name: /^Course \+ my notes/ }))
    await user.click(screen.getByRole('button', { name: 'Ask' }))
    expect(api.askCourseAi).toHaveBeenLastCalledWith(3, 'What does 3NF remove?', expect.anything(), { mode: 'COURSE_AND_NOTES', moduleId: null })
    expect(await screen.findByText('My note', { exact: true })).toBeInTheDocument()
  })
})

describe('Ask My Course', () => {
  it('scopes a question to one module and links each source to its page', async () => {
    vi.mocked(api.askCourseAi).mockResolvedValue({
      answer: 'A join combines rows.', insufficientContext: false,
      citations: [{ sourceType: 'TOPIC', sourceId: 31, title: 'Joins', fileName: null, pageNumber: null, slideNumber: null, pageId: 31 }],
    })
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <AskPanel lmsCourseId={3} modules={[{ id: 7, title: 'Unit 2: SQL' }]} />
      </MemoryRouter>,
    )
    await user.selectOptions(screen.getByRole('combobox', { name: 'Module' }), 'Unit 2: SQL')
    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'What is a join?')
    await user.click(screen.getByRole('button', { name: 'Ask' }))
    expect(api.askCourseAi).toHaveBeenLastCalledWith(3, 'What is a join?', expect.anything(), { mode: 'COURSE', moduleId: 7 })
    expect(await screen.findByRole('link', { name: /^Open page/ })).toHaveAttribute('href', '/courses/3/pages/31')

    // Notes only searches no module.
    await user.click(screen.getByRole('radio', { name: /^My notes only/ }))
    expect(screen.queryByRole('combobox', { name: 'Module' })).not.toBeInTheDocument()
  })
})
