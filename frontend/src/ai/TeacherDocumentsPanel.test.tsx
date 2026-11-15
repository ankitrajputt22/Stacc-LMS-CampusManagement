import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { AiTeacherDocument } from '../types/ai'
import { TeacherDocumentsPanel } from './TeacherDocumentsPanel'

vi.mock('../api/aiApi')

const document: AiTeacherDocument = {
  id: 9, title: 'Exam focus', content: 'Third normal form.', status: 'ACTIVE',
  createdAt: '2026-11-15T05:00:00Z', updatedAt: '2026-11-15T05:00:00Z',
}

function renderPanel() {
  render(
    <QueryClientProvider client={testQueryClient()}>
      <TeacherDocumentsPanel lmsCourseId={3} />
    </QueryClientProvider>,
  )
}

describe('TeacherDocumentsPanel', () => {
  it('adds a document and asks for a refresh', async () => {
    vi.mocked(api.getTeacherDocuments).mockResolvedValueOnce([]).mockResolvedValue([document])
    vi.mocked(api.createTeacherDocument).mockResolvedValue(document)
    const user = userEvent.setup()
    renderPanel()

    await user.click(await screen.findByRole('button', { name: 'Add teacher document' }))
    await user.type(screen.getByRole('textbox', { name: 'Title' }), 'Exam focus')
    await user.type(screen.getByRole('textbox', { name: /^Text/ }), 'Third normal form.')
    await user.click(screen.getByRole('button', { name: 'Add document' }))

    expect(api.createTeacherDocument).toHaveBeenCalledWith(3, 'Exam focus', 'Third normal form.')
    expect(await screen.findByText('Added Exam focus. Refresh AI knowledge to include the change.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /^Edit/ })).toBeInTheDocument()
  })

  it('archives only after confirming', async () => {
    vi.mocked(api.getTeacherDocuments).mockResolvedValueOnce([document]).mockResolvedValue([])
    vi.mocked(api.archiveTeacherDocument).mockResolvedValue({ ...document, status: 'ARCHIVED' })
    const user = userEvent.setup()
    renderPanel()

    await user.click(await screen.findByRole('button', { name: /^Archive/ }))
    expect(api.archiveTeacherDocument).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Archive document' }))
    expect(api.archiveTeacherDocument).toHaveBeenCalledWith(9)
    expect(await screen.findByText('No teacher documents yet.')).toBeInTheDocument()
  })
})
