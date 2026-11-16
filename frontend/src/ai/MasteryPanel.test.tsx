import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { testQueryClient } from '../test/queryTestUtils'
import { MasteryPanel } from './MasteryPanel'

vi.mock('../api/aiApi')

describe('MasteryPanel', () => {
  it('shows each page’s level and clears evidence only after confirming', async () => {
    vi.mocked(api.getMastery).mockResolvedValueOnce([
      { topicId: 1, title: 'Joins', level: 'MASTERED', mastery: 0.9, confidence: 0.8, evidence: 12, lastPractisedAt: '2026-11-16T05:00:00Z' },
      { topicId: 2, title: 'Indexes', level: 'NOT_STARTED', mastery: 0, confidence: 0, evidence: 0, lastPractisedAt: null },
    ]).mockResolvedValue([])
    vi.mocked(api.clearMastery).mockResolvedValue()
    const user = userEvent.setup()
    render(<QueryClientProvider client={testQueryClient()}><MasteryPanel lmsCourseId={3} /></QueryClientProvider>)
    expect(await screen.findByText('Mastered')).toBeInTheDocument()
    expect(screen.getByText('90% · high confidence · 12 tries')).toBeInTheDocument()
    expect(screen.getByText('Not started')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Clear my evidence' }))
    expect(api.clearMastery).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Clear evidence' }))
    expect(api.clearMastery).toHaveBeenCalledWith(3)
  })
})
