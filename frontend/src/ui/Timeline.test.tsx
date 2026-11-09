import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Timeline } from './Timeline'

describe('Timeline', () => {
  it('lists entries in order with who and when, and says when there is nothing', () => {
    const { rerender } = render(
      <Timeline
        label="History"
        entries={[
          { key: 1, title: 'Ticket created.', actor: 'student1', at: '2026-11-10T09:00:00Z' },
          { key: 2, title: 'Status changed.', actor: 'admin1', detail: 'A comment' },
        ]}
      />,
    )
    const items = screen.getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('Ticket created.')
    expect(items[0]).toHaveTextContent('student1')
    expect(screen.getByText('A comment')).toBeInTheDocument()
    rerender(<Timeline label="History" entries={[]} empty="No activity yet." />)
    expect(screen.getByText('No activity yet.')).toBeInTheDocument()
  })
})
