import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { PageErrorBoundary } from './PageErrorBoundary'

function Broken(): never {
  throw new Error('chunk failed')
}

describe('PageErrorBoundary', () => {
  it('shows the shared error state instead of a blank page', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    render(<PageErrorBoundary><Broken /></PageErrorBoundary>)
    expect(screen.getByRole('alert')).toHaveTextContent('This page couldn’t be shown')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('shows the page when nothing fails', () => {
    render(<PageErrorBoundary><p>Fine</p></PageErrorBoundary>)
    expect(screen.getByText('Fine')).toBeInTheDocument()
  })
})
