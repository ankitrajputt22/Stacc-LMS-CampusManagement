import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/lmsCourseManagementApi'
import { testQueryClient } from '../test/queryTestUtils'
import { CopyContentCard } from './CopyContentCard'

vi.mock('../api/lmsCourseManagementApi', () => ({ getCopySources: vi.fn(), copyCourseContent: vi.fn() }))

function show(reload = vi.fn()) {
  return render(
    <QueryClientProvider client={testQueryClient()}>
      <CopyContentCard lmsCourseId={8} reload={reload} />
    </QueryClientProvider>,
  )
}

describe('CopyContentCard', () => {
  it('copies from the chosen earlier offering only after confirmation and says what arrived', async () => {
    vi.mocked(api.getCopySources).mockResolvedValue([
      { lmsCourseId: 7, courseCode: 'CS250', academicSessionCode: '2025-26', semesterNumber: 2, programCode: 'BTECH-CSE',
        sectionCode: null, lmsCourseStatus: 'ARCHIVED' },
    ])
    vi.mocked(api.copyCourseContent).mockResolvedValue({ modules: 1, pages: 2, files: 0, syllabus: true })
    const reload = vi.fn()
    const user = userEvent.setup()
    show(reload)

    await user.selectOptions(await screen.findByLabelText('Copy from'), '2025-26 · BTECH-CSE Semester 2 (archived)')
    await user.click(screen.getByRole('button', { name: 'Copy content' }))
    expect(api.copyCourseContent).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Copy content' }))

    expect(api.copyCourseContent).toHaveBeenCalledWith(8, 7)
    expect(await screen.findByText(/Copied 1 module, 2 pages, 0 files, and the syllabus text as drafts\./)).toBeInTheDocument()
    expect(reload).toHaveBeenCalled()
  })

  it('shows nothing when there is no earlier offering to copy from', async () => {
    vi.mocked(api.getCopySources).mockResolvedValue([])
    const { container } = show()

    await vi.waitFor(() => expect(api.getCopySources).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })
})
