import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/hackathonApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import type { Hackathon } from '../types/hackathons'
import { HackathonsPage } from './HackathonsPage'

vi.mock('../api/hackathonApi')

const auth: AuthContextValue = {
  isChecking: false,
  isAuthenticated: true,
  account: { accountId: 7, loginId: 'student2', roles: ['STUDENT'] },
  passwordChangeRequired: false,
  passwordChanged: vi.fn(),
  signedOutByUser: false,
  login: vi.fn(),
  logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}

const base: Hackathon = {
  hackathonId: 1, title: 'Code Sprint', description: null, startsAt: '2026-12-01T04:00:00Z', endsAt: '2026-12-02T04:00:00Z', maxTeamSize: 2,
  status: 'PUBLISHED', takingWork: true, myTeamId: null,
  teams: [
    { teamId: 1, name: 'Owls', memberLoginIds: ['student1', 'student3'], submitted: true, submission: null },
    { teamId: 2, name: 'Hawks', memberLoginIds: ['student4'], submitted: false, submission: null },
  ],
  results: [],
}

function renderPage(hackathons: Hackathon[]) {
  vi.mocked(api.getHackathons).mockResolvedValue(hackathons)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AuthContext value={auth}>
        <HackathonsPage />
      </AuthContext>
    </QueryClientProvider>,
  )
}

describe('HackathonsPage', () => {
  it('offers to join only teams with a free place and shows no results before release', async () => {
    renderPage([base])
    expect(await screen.findByRole('button', { name: /^Join\s*Hawks$/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Join\s*Owls$/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Results of Code Sprint' })).not.toBeInTheDocument()
  })

  it('shows released results with places and totals', async () => {
    renderPage([{ ...base, status: 'RESULTS_RELEASED', takingWork: false, results: [{ place: 1, teamName: 'Owls', submissionTitle: 'Bus tracker', total: 172 }] }])
    expect((await screen.findAllByText('Bus tracker')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('172').length).toBeGreaterThan(0)
  })
})
