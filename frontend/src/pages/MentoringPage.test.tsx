import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/mentoringApi'
import type { MentoringPairing } from '../types/mentoring'
import { MentoringPage } from './MentoringPage'

vi.mock('../api/mentoringApi')

const pairing: MentoringPairing = {
  assignmentId: 10, mentorLoginId: 'faculty1', menteeLoginId: 'student1', iAmMentor: false, active: true, startedAt: '2026-11-01T00:00:00Z', endedAt: null,
}

function renderPage(mine: MentoringPairing[], path: string) {
  vi.mocked(api.getMyPairings).mockResolvedValue(mine)
  vi.mocked(api.getPairing).mockResolvedValue({
    pairing: mine[0],
    meetings: [{ meetingId: 20, title: 'First meeting', heldAt: '2026-11-05T09:00:00Z', sharedNotes: 'Plan the semester.', privateNotes: null,
      actionItems: [{ itemId: 30, description: 'Draft a study plan', owner: 'MENTEE', dueAt: null, completedAt: null }] }],
  })
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <Routes>
          <Route path="/mentoring" element={<MentoringPage />} />
          <Route path="/mentoring/:assignmentId" element={<MentoringPage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  )
}

describe('MentoringPage', () => {
  it('lists the pairing and the student’s own open item on the home page', async () => {
    renderPage([pairing], '/mentoring')
    expect(await screen.findByRole('link', { name: 'View meetings with faculty1' })).toHaveAttribute('href', '/mentoring/10')
    expect(await screen.findByRole('button', { name: /^Mark done\s*Draft a study plan$/ })).toBeInTheDocument()
  })

  it('shows the student shared notes and their action item, with no meeting form', async () => {
    renderPage([pairing], '/mentoring/10')
    expect(await screen.findByText('Plan the semester.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Mark done/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Record meeting' })).not.toBeInTheDocument()
    expect(screen.queryByText(/Private notes/)).not.toBeInTheDocument()
  })

  it('gives the mentor the meeting form', async () => {
    renderPage([{ ...pairing, iAmMentor: true }], '/mentoring/10')
    expect(await screen.findByRole('button', { name: 'Record meeting' })).toBeInTheDocument()
  })
})
