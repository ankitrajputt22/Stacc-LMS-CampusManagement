import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/innovationApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { InnovationPage } from './InnovationPage'

vi.mock('../api/innovationApi')

const auth: AuthContextValue = {
  isChecking: false,
  isAuthenticated: true,
  account: { accountId: 7, loginId: 'student1', roles: ['STUDENT'] },
  passwordChangeRequired: false,
  passwordChanged: vi.fn(),
  signedOutByUser: false,
  login: vi.fn(),
  logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}

describe('InnovationPage', () => {
  it('shows a student their decided idea with comments and no reviewer names or review queue', async () => {
    vi.mocked(api.getMyIdeas).mockResolvedValue([
      { ideaId: 1, title: 'Solar benches', problem: 'No outdoor charging.', solution: 'Solar USB benches.', status: 'ACCEPTED', submitterLoginId: null,
        submittedAt: '2026-11-01T00:00:00Z', decisionNote: 'Pilot approved.', decidedAt: '2026-11-05T00:00:00Z',
        reviews: [{ reviewerLoginId: null, score: 0, comment: 'Cheap and useful.', reviewedAt: '2026-11-03T00:00:00Z' }] },
    ])
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AuthContext value={auth}>
          <InnovationPage />
        </AuthContext>
      </QueryClientProvider>,
    )
    expect(await screen.findByText('Cheap and useful.')).toBeInTheDocument()
    expect(screen.getByText('Decision note: Pilot approved.')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Ideas to review' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Withdraw/ })).not.toBeInTheDocument()
  })
})
