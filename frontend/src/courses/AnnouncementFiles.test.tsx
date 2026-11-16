import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AnnouncementFiles } from './AnnouncementFiles'

const FILE = { id: 4, fileName: 'timetable.pdf', contentType: 'application/pdf', sizeBytes: 2048 }

describe('AnnouncementFiles', () => {
  it('shows nothing without files', () => {
    const { container } = render(<AnnouncementFiles files={[]} title="Exams" download={vi.fn()} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('lists each file with Download, and Remove only when it may be removed', async () => {
    const onRemove = vi.fn()
    const download = vi.fn().mockRejectedValue(new Error('offline'))
    render(<AnnouncementFiles files={[FILE]} title="Exams" download={download} onRemove={onRemove} />)
    expect(screen.getByRole('list', { name: 'Files of Exams' })).toHaveTextContent('timetable.pdf')
    await userEvent.click(screen.getByRole('button', { name: /^Remove\s*timetable\.pdf$/ }))
    expect(onRemove).toHaveBeenCalledWith(FILE)
    await userEvent.click(screen.getByRole('button', { name: /^Download\s*timetable\.pdf$/ }))
    expect(download).toHaveBeenCalledWith(4)
    expect(await screen.findByText('Couldn’t download the file. Try again.')).toBeInTheDocument()
  })
})
