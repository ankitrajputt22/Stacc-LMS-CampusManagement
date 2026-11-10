import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/mentoringApi'
import type { MentoringPairing } from '../types/mentoring'
import { MentoringPage } from './MentoringPage'

vi.mock('../api/mentoringApi')

const pairing: MentoringPairing = {
  assignmentId: 10, mentorLoginId: 'faculty1', menteeLoginId: 'student1', iAmMentor: false, active: true, startedAt: '2026-11-01T00:00:00Z', endedAt: null,
}

function renderPage(mine: MentoringPairing[]) {
  vi.mocked(api.getMyPairings).mockResolvedValue(mine)
  vi.mocked(api.getPairing).mockResolvedValue({
    pairing: mine[0],
    meetings: [{ meetingId: 20, title: 'First meeting', heldAt: '2026-11-05T09:00:00Z', sharedNotes: 'Plan the semester.', privateNotes: null,
      actionItems: [{ itemId: 30, description: 'Draft a study plan', owner: 'MENTEE', dueAt: null, completedAt: null }] }],
  })
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MentoringPage />
    </QueryClientProvider>,
  )
}

describe('MentoringPage', () => {
  it('shows the student shared notes and their action item, with no meeting form', async () => {
    renderPage([pairing])
    expect(await screen.findByText('Plan the semester.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Mark done/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Record meeting' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Private notes/)).not.toBeInTheDocument()
  })

  it('gives the mentor the meeting form', async () => {
    renderPage([{ ...pairing, iAmMentor: true }])
    expect(await screen.findByRole('button', { name: 'Record meeting' })).toBeInTheDocument()
  })
})
