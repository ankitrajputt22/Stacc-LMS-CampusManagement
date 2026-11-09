import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { StudentStepper } from './StudentStepper'

const entries = [
  { key: 1, label: 'student1', to: '?submission=1' },
  { key: 2, label: 'student2', to: '?submission=2' },
  { key: 3, label: 'student3', to: '?submission=3' },
]

describe('StudentStepper', () => {
  it('links to the previous and next student and shows the position', () => {
    render(<MemoryRouter><StudentStepper entries={entries} currentKey={2} what="submissions" /></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Previous: student1' })).toHaveAttribute('href', '/?submission=1')
    expect(screen.getByRole('link', { name: 'Next: student3' })).toHaveAttribute('href', '/?submission=3')
    expect(screen.getByText('2 of 3')).toBeInTheDocument()
  })

  it('shows no Previous on the first student and nothing for an unknown one', () => {
    const { rerender } = render(<MemoryRouter><StudentStepper entries={entries} currentKey={1} what="submissions" /></MemoryRouter>)
    expect(screen.queryByRole('link', { name: /Previous/ })).not.toBeInTheDocument()
    rerender(<MemoryRouter><StudentStepper entries={entries} currentKey={9} what="submissions" /></MemoryRouter>)
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })
})
