import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/lmsGradebookApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { Gradebook } from '../types/lms'
import { GradebookEditor } from './GradebookEditor'

vi.mock('../api/lmsGradebookApi', () => ({ getGradebook: vi.fn(), addGradeItem: vi.fn(), publishGradeColumn: vi.fn() }))

const gradebook: Gradebook = {
  columns: [
    { type: 'ASSIGNMENT', sourceId: 12, title: 'Arrays', maxMarks: 20, dueAt: null, published: true, publishedAt: '2026-11-10T05:00:00Z' },
    { type: 'MANUAL', sourceId: 70, title: 'Class test', maxMarks: 25, dueAt: null, published: false, publishedAt: null },
  ],
  students: [
    {
      courseEnrollmentId: 41,
      studentLoginId: 'student1',
      cells: [
        { marks: 17, status: 'GRADED', feedback: null },
        { marks: null, status: 'NO_MARKS', feedback: null },
      ],
      totalMarks: 17,
      totalOutOf: 20,
    },
    {
      courseEnrollmentId: 42,
      studentLoginId: 'student2',
      cells: [
        { marks: null, status: 'NEEDS_GRADING', feedback: null },
        { marks: 20, status: 'GRADED', feedback: null },
      ],
      totalMarks: 20,
      totalOutOf: 25,
    },
  ],
}

function renderEditor() {
  vi.mocked(api.getGradebook).mockResolvedValue(gradebook)
  return render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>
        <GradebookEditor lmsCourseId={8} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('GradebookEditor', () => {
  it('shows marks per column with totals and finds a student', async () => {
    const user = userEvent.setup()
    renderEditor()

    const table = (await screen.findAllByRole('table'))[0]
    expect(within(table).getByRole('columnheader', { name: 'Arrays (/20)' })).toBeInTheDocument()
    expect(within(table).getByText('17 / 20')).toBeInTheDocument()
    expect(within(table).getByText('Needs grading')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Search by college ID'), 'student2')
    expect(within(table).queryByText('17 / 20')).not.toBeInTheDocument()
    expect(within(table).getByText('20 / 25')).toBeInTheDocument()
  })

  it('publishes an unpublished column after confirming', async () => {
    vi.mocked(api.publishGradeColumn).mockResolvedValue(gradebook.columns[1])
    const user = userEvent.setup()
    renderEditor()

    await user.click(await screen.findByRole('button', { name: /^Publish grades\s*: Class test$/ }))
    expect(api.publishGradeColumn).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Publish grades' }))
    expect(api.publishGradeColumn).toHaveBeenCalledWith(8, 'MANUAL', 70)
    expect(screen.queryByRole('button', { name: /^Publish grades\s*: Arrays$/ })).not.toBeInTheDocument()
  })

  it('checks a new manual item before adding it', async () => {
    vi.mocked(api.addGradeItem).mockResolvedValue({ id: 71 })
    const user = userEvent.setup()
    renderEditor()

    await user.type(await screen.findByLabelText('Item title'), 'Viva')
    await user.type(screen.getByLabelText('Maximum marks'), '0')
    await user.click(screen.getByRole('button', { name: 'Add item' }))
    expect(api.addGradeItem).not.toHaveBeenCalled()
    await user.clear(screen.getByLabelText('Maximum marks'))
    await user.type(screen.getByLabelText('Maximum marks'), '10')
    await user.click(screen.getByRole('button', { name: 'Add item' }))
    expect(api.addGradeItem).toHaveBeenCalledWith(8, { title: 'Viva', maxMarks: 10 })
  })
})
